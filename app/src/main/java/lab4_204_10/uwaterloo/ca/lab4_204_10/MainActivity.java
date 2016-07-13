package lab4_204_10.uwaterloo.ca.lab4_204_10;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.*;
import android.view.*;
import android.view.animation.RotateAnimation;
import android.widget.*;
import java.io.File;
import java.util.*;
import mapper.*;

public class MainActivity extends AppCompatActivity {
    // region Fields Declaration
    // graphics variables
    private static MapView mv;
    private static NavigationalMap map;
    private static ImageView image;
    private static Bitmap bitmap;

    // steps variables
    private int steps;
    private float[] pointList = new float[3];
    private static float currentDegree;

    // stage variables
    private int stage;
    private final int STAGE_IDLE = 0;
    private final int STAGE_QUARTER = 1;
    private final int STAGE_HALF = 2;
    private final int STAGE_THREE_QUARTERS = 3;

    // orientation variables
    float[] gravity = new float[3];
    float[] magnetic = new float[3];
    float[] result = new float[3];
    // endregion

    @Override
    public  void  onCreateContextMenu(ContextMenu  menu , View v, ContextMenu.ContextMenuInfo menuInfo) {
        mv.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return mv.onContextItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // region Graphic Set up
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LinearLayout layout = ((LinearLayout)findViewById(R.id.layout));

        // Hacky Android Permission fix
        /*if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
            //ask for permission
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 2909);
        }*/

        // local variables to determine the map scale
        float[] scale = Data.map.equals("E2-3344.svg") ? new float[] { 45, 35 } : new float[] { 70, 65 };

        // load map view
        mv = new  MapView(getApplicationContext(), 1200, 800, scale[0], scale[1]);
        registerForContextMenu(mv);
        String downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        File dir = new File(downloadDir);
        map = MapLoader.loadMap(dir,Data.map);
        mv.setMap(map);
        mv.addListener(new PositionEventHandler());
        layout.addView(mv);
        // endregion

        // region Sensor Set Up
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor[] sensor = new Sensor[] { sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) };
        // endregion

        // region Create Title
        TextView title1 = new TextView(getApplicationContext());
        title1.setText("--- Steps Taken ---" );
        title1.setTextColor(Color.BLACK);
        layout.addView(title1);

        TextView output1 = new TextView(getApplicationContext());
        output1.setTextColor(Color.BLACK);
        layout.addView(output1);

        TextView title2 = new TextView(getApplicationContext());
        title2.setText("--- Direction Go ---" );
        title2.setTextColor(Color.BLACK);
        layout.addView(title2);

        bitmap = BitmapFactory.decodeFile(downloadDir + "/up.png");
        image = new ImageView(getApplicationContext());
        image.setImageBitmap(bitmap);
        layout.addView(image);
        // endregion

        // region Event Registration
        sensorManager.registerListener(new SensorEventHandler(new TextView[] {output1}), sensor[0], SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(new SensorEventHandler(new TextView[] {output1}), sensor[1], SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(new SensorEventHandler(new TextView[] {output1}), sensor[2], SensorManager.SENSOR_DELAY_NORMAL);
        // endregion

        // region Reset Button Set Up
        Button reset = new Button(getApplicationContext());
        reset.setText("Reset");
        layout.addView(reset);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pointList = new float[3];
                stage = STAGE_IDLE;
                steps = 0;
                mv.setUserPath(new ArrayList<PointF>());
                mv.setUserPoint(mv.getOriginPoint());
            }
        });
        // endregion
    }

    /* a method that simulate low pass filter */
    protected static float filter(float oldValue, float newValue) {
        return oldValue + (newValue - oldValue)/550;
    }

    /* a method that calculate the path from origin to destination */
    protected void calculatePath(PointF start, PointF end) {
        if (mv.getUserPoint().x < 1 || mv.getUserPoint().y < 1 || mv.getDestinationPoint().x < 1 || mv.getDestinationPoint().y < 1) return;

        // declare ArrayList for the path
        ArrayList<PointF> path = new ArrayList<PointF>();
        path.add(start);

        // local fields for path determination
        PointF ps = new PointF(start.x, start.y);
        PointF pe = new PointF(end.x, end.y);
        boolean upEnd = false;
        boolean downEnd = false;
        boolean upDirection = (end.y >= start.y);
        boolean downDirection = (end.y < start.y);

        // declare a list of intercept point for obstacle check
        List<InterceptPoint> blocking = null;

        // main calculation part
        do {
            if (upDirection && !upEnd) {  // destination is higher than origin case
                // create new Y point that increment Y position
                PointF newY = new PointF(ps.x, ps.y + Data.distance);

                // check if the user cannot go up anymore
                if (map.calculateIntersections(ps, newY).size() > 0) {
                    // detect upper bound limit -> set up end to true
                    upEnd = true;
                    downDirection = true;
                    continue;
                }

                // assign new Y position
                ps = newY;
                pe.y = ps.y;
                blocking = map.calculateIntersections(ps, pe);
            } else if (downDirection && !downEnd) {     // destination is lower than origin case
                // create new Y point that decrement Y position
                PointF newY = new PointF(ps.x, ps.y - Data.distance);

                // check if the user cannot go dow anymore
                if (map.calculateIntersections(ps, newY).size() > 0) {
                    // detect lower bound limit -> set down end to true
                    downEnd = true;
                    upDirection = true;
                    continue;
                }

                // assign new Y position
                ps = newY;
                pe.y = ps.y;
                blocking = map.calculateIntersections(ps, pe);
            } else {
                // the case if the origin is being surrounded -> inform user
                showDialog("Sorry", "Could not calculate the path");
                return;
            }

        } while (blocking != null && blocking.size() > 0);

        // the case if there is blocking to the end point -> inform user
        if (map.calculateIntersections(pe, end).size() > 0) {
            showDialog("Sorry", "Could not calculate the path");
            return;
        }

        // complete the path by adding the end point to the list
        path.add(ps);
        path.add(pe);
        path.add(end);
        mv.setUserPath(path);
    }

    /* a method that calculate the direction from the current point to destination */
    protected static void calculateDirection(float heading, PointF currentPoint, PointF endPoint) {
        // region Displaying Direction to The Destination
        // get the angle to the destination
        float angle = (float) Math.atan((endPoint.y - currentPoint.y)/(endPoint.x - currentPoint.x));

        // rotate the heading direction to fit with the angle
        heading += Math.PI/2;
        if (heading > Math.PI) {
            heading = -2 * (float)(Math.PI) + heading;
        }
        heading = heading >= 0 ? (float)Math.PI - heading : -1 * ((float)Math.PI + heading);

        // local field for update
        float result;

        // matching angle to store the result
        if (heading >= 0 && angle >= 0) {
            result = heading - angle;
        } else if (heading >= 0 && angle < 0) {
            result = heading + Math.abs(angle);
        } else if (heading < 0 && angle >= 0) {
            result = -1 * (Math.abs(heading) + angle);
        } else {
            result = Math.abs(angle) - Math.abs(heading);
        }
        // endregion

        // region Displaying North
        // float result = -1*heading;
        // endregion

        // set rotate animation
        final RotateAnimation rotateAnim = new RotateAnimation((float)Math.toDegrees(currentDegree), (float)Math.toDegrees(result),
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);

        // start rotation
        rotateAnim.setDuration(0);
        rotateAnim.setFillAfter(true);
        image.startAnimation(rotateAnim);
        currentDegree = result;
    }

    /* a method that shows the dialog */
    protected void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        pointList = new float[3];
                        stage = STAGE_IDLE;
                        steps = 0;
                        mv.setUserPath(new ArrayList<PointF>());
                        mv.setUserPoint(mv.getOriginPoint());
                    }
                }).show();
    }

    /*
     * A private class that implement PositionListener for MapView
     */
    private class PositionEventHandler implements PositionListener {
        @Override
        public void originChanged(MapView source, PointF loc) {
            source.setOriginPoint(loc);
            source.setUserPoint(loc);
            calculatePath(loc, mv.getDestinationPoint());
        }

        @Override
        public void destinationChanged(MapView source, PointF dest) {
            source.setDestinationPoint(dest);
            calculatePath(mv.getOriginPoint(), dest);
        }
    }

    /*
     * A private class that handle sensor event
     */
    private class SensorEventHandler implements SensorEventListener {
        // field for showing result
        private TextView[] output;

        /* constructor that initialize output controls */
        public SensorEventHandler(TextView[] output) {
            this.output = output;
        }

        /* implement dummy method */
        public void onAccuracyChanged(Sensor s, int i) {
            // dummy implementation
        }

        /* on sensor changed event to calculate step from sensor value */
        public void onSensorChanged(SensorEvent event) {

            // get the sensor type
            switch(event.sensor.getType()) {
                // linear acceleration for step count
                case Sensor.TYPE_LINEAR_ACCELERATION:
                {
                    // get the x, y, z component values as a float array
                    pointList = new float[]{filter(pointList[0], event.values[0]), filter(pointList[1], event.values[1]), filter(pointList[2], event.values[2])};
                    float z = pointList[2];

                    // region Finite State Machine
                    switch (stage) {
                        case STAGE_IDLE:
                            if (z >= 0.011 && z < 0.024)
                                stage = STAGE_QUARTER;
                            break;

                        case STAGE_QUARTER:
                            if (z >= 0.024 && z < 0.037)
                                stage = STAGE_HALF;
                            break;

                        case STAGE_HALF:
                            if (z >= 0.037 && z < 0.05)
                                stage = STAGE_THREE_QUARTERS;
                            else if (z < 0.024)
                                stage = STAGE_IDLE;
                            break;

                        case STAGE_THREE_QUARTERS:
                            if (z >= 0.05 && z < 0.08) {
                                PointF p = mv.getUserPoint();
                                PointF newP = new PointF(p.x + (float)Math.sin(result[0]) * Data.distance, p.y - (float)Math.cos(result[0]) * Data.distance);
                                List<InterceptPoint> list = map.calculateIntersections(p, newP);
                                if (list.size() < 1) {
                                    steps++;
                                    mv.setUserPoint(newP);
                                    calculatePath(newP, mv.getDestinationPoint());
                                    if (Math.abs(mv.getDestinationPoint().y - newP.y) < Data.distance && Math.abs(mv.getDestinationPoint().x - newP.x) < Data.distance)
                                        if (mv.getUserPoint().x > 0 && mv.getUserPoint().y > 0 && mv.getDestinationPoint().x > 0 && mv.getDestinationPoint().y > 0)
                                            showDialog("Congratulations", "You reached your destination!");
                                }
                                stage = STAGE_IDLE;
                            } else if (z < 0.037) {
                                stage = STAGE_IDLE;
                            }
                            break;
                    }
                    // endregion

                    // display new step count and point
                    output[0].setText("steps: " + steps);
                    break;
                }

                // accelerometer for matrix
                case Sensor.TYPE_ACCELEROMETER:
                    gravity = event.values;
                    break;

                // magnetic field for matrix
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magnetic = event.values;
                    break;
            }
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, magnetic)) {
                SensorManager.getOrientation(R, result);
            }
            calculateDirection(result[0], mv.getUserPoint(), mv.getDestinationPoint());
        }
    }
}
