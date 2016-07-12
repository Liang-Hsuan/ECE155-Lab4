package lab4_204_10.uwaterloo.ca.lab4_204_10;

import android.content.*;
import android.support.v7.app.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

/*
 * Entry class that require user to enter a step distance and select a map
 */
public class FirstActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        // region Spinner Set Up
        Spinner dropdown = (Spinner)findViewById(R.id.spinner);
        String[] items = new String[]{"E2-3344.svg", "Lab-room.svg", "Lab-room-bonus-destination-and-start.svg",
                "Lab-room-inclined-9.4deg.svg", "Lab-room-inclined-16deg.svg", "Lab-room-peninsula.svg", "Lab-room-peninsula-9.4deg.svg",
                "Lab-room-peninsula-16deg.svg", "Lab-room-unconnected.svg"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);
        // endregion

        // region Enter Button Set Up
        Button enter = (Button) findViewById(R.id.button);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get the control for user input
                EditText view = (EditText) findViewById(R.id.box);
                Spinner spinner = (Spinner) findViewById(R.id.spinner);

                // get distance of a step
                try {
                    Data.distance = Float.valueOf(view.getText().toString());

                    // invalid input case -> show warning
                    if (Data.distance > 1 || Data.distance < 0.2) {
                        showDialog("Warning", "Please enter a value between 0.2 and 1 [meters]");
                        return;
                    }
                } catch (Exception ex) {
                    showDialog("Warning", "Please enter a valid number");
                    return;
                }

                // get the map
                Data.map = spinner.getSelectedItem().toString();

                // switch to main activity
                startActivity(new Intent(FirstActivity.this, MainActivity.class));
            }
        });
        // endregion
    }

    /* a method that show dialog */
    private void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        return;
                    }
                }).show();
    }
}

