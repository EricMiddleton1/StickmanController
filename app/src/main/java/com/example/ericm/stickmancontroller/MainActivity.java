package com.example.ericm.stickmancontroller;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity
    implements AdapterView.OnItemSelectedListener, SeekBar.OnSeekBarChangeListener {
    public final static String EXTRA_MESSAGE = "com.example.ericm.stickmancontroller.MESSAGE";
    NetworkTask networkTask;

    private final static float BATTERY_WARNING_VOLTAGE = 10.f;
    private TextView batteryText;
    private ColorStateList batteryTextColors;
    private boolean batteryLow;

    private byte[] params;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize the battery text
        batteryText = (TextView)findViewById(R.id.text_battery);
        batteryText.setText("Battery Voltage: ");

        //Store the battery text colors
        batteryTextColors = batteryText.getTextColors();
        batteryLow = false;

        //Register spinner callback
        Spinner modeSpinner = (Spinner) findViewById(R.id.mode_spinner);
        modeSpinner.setOnItemSelectedListener(this);

        //Register brightness seekbar callback
        SeekBar seekBar = (SeekBar) findViewById(R.id.brightness_seekbar);
        seekBar.setOnSeekBarChangeListener(this);


        //Create network object
        networkTask = new NetworkTask(this);

        //Run the network task
        networkTask.execute();
    }

    /*
    @Override
    protected void onStop() {
        //Stop the network task
        if(networkTask != null) {
            networkTask.cancel(true);

            networkTask = null;
        }
    }
    */

    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        //Send a mode change packet (2)
        byte[] payload = new byte[1];
        payload[0] = (byte)id;
        networkTask.sendPacket(new Packet((byte)2, payload));
    }

    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        //Send packet
        byte[] payload = new byte[2];

        payload[0] = (byte)0;
        payload[1] = (byte)progress;

        networkTask.sendPacket(new Packet((byte)3, payload));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void onDisplayButton(View view) {
        EditText editText = (EditText) findViewById(R.id.chest_message);

        String msg = editText.getText().toString();

        //Construct packet
        byte[] payload = new byte[msg.length() + 1];
        payload[0] = 4;

        for(int i = 0; i < msg.length(); i++) {
            payload[i+1] = (byte)msg.charAt(i);
        }

        //Send packet
        //networkTask.sendPacket(new Packet((byte)3, payload));
    }

    protected void setModeSpinner(String[] modes) {
        Spinner modeSpinner = (Spinner) findViewById(R.id.mode_spinner);

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        modeSpinner.setAdapter(adapter);
    }


    private void onPacketReceive(Packet packet) {
        byte[] payload = packet.getPayload();

        switch(packet.getPacketType()) {
            case 1:
                //LIST_MODE_RESPONSE

                if(payload == null || payload.length < 1) {
                    //Something went wrong
                    Toast.makeText(MainActivity.this,
                            "Error: list mode response packet has invalid payload",
                            Toast.LENGTH_SHORT).show();
                }
                else {
                    String modes[] = new String[(int)(payload[0] & 0xFF)];

                    modes[0] = "";

                    for(int i = 1, j = 0; i < payload.length && j < modes.length; i++) {
                        if(payload[i] == 0x00) {
                            j++;
                            if(j < modes.length)
                                modes[j] = "";
                        }
                        else {
                            modes[j] = modes[j].concat(Character.toString((char)payload[i]));
                        }
                    }

                    //Set spinner items
                    setModeSpinner(modes);
                }

                break;

            case 4:
                //MODE_PARAM_GET
                break;

            case 5:
                //BATTERY_LEVEL

                if(payload == null || payload.length != 2) {
                    //This is an error!
                    Toast.makeText(MainActivity.this,
                            "Error: Battery packet contains invalid payload!", Toast.LENGTH_SHORT).show();
                }
                else {
                    int voltInt = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    float voltage = (float)voltInt / 1000.f;

                    //Set battery text
                    batteryText.setText("Battery Voltage: " + voltage + "v");

                    if(voltage < BATTERY_WARNING_VOLTAGE && !batteryLow) {
                        batteryLow = true;

                        //Set text to red
                        batteryText.setTextColor(Color.RED);
                    }
                    else if(batteryLow && voltage >= BATTERY_WARNING_VOLTAGE) {
                        batteryLow = false;

                        //Reset text color
                        batteryText.setTextColor(batteryTextColors);
                    }
                }
                break;

            default:
                Toast.makeText(MainActivity.this,
                        "Packet received with type " + packet.getPacketType(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onConnect() {
        //Send LIST_MODE_QUERY (0) packet
        networkTask.sendPacket(new Packet((byte)0, null));
    }



    //Network stuff
    private class NetworkTask extends AsyncTask<Void, Void, Void>
    {
        private MainActivity activity;
        private Socket tcpSocket;
        static final private byte HEADER_VALUE = (byte)0xAF;

        public NetworkTask(MainActivity activity) {
            this.activity = activity;
        }

        public void sendPacket(Packet packet) {
            int payloadLength = 0;

            if(packet.getPayload() != null)
                payloadLength = packet.getPayload().length;

            byte[] buffer = new byte[4 + payloadLength];

            buffer[0] = HEADER_VALUE;
            buffer[1] = packet.getPacketType();
            buffer[2] = (byte)(payloadLength >> 8);
            buffer[3] = (byte)(payloadLength & 0xFF);

            //Copy the payload into the buffer
            for(int i = 0; i < payloadLength; i++) {
                buffer[4 + i] = packet.getPayload()[i];
            }

            //Get output stream
            try {
                OutputStream output = tcpSocket.getOutputStream();

                //Send the packet
                output.write(buffer);
            }
            catch(java.io.IOException e) {
                return;
            }
        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(Void... params) {
            InputStream inputStream = null;
            boolean connected = false;

            try {
                //Connect to light suit
                InetSocketAddress socketAddress = new InetSocketAddress("192.168.1.1", 8888);
                //tcpSocket = new Socket(InetAddress.getByName("192.168.1.1"), 8888);
                tcpSocket = new Socket();

                tcpSocket.connect(socketAddress, 1000);

                //Open input stream
                inputStream = tcpSocket.getInputStream();

                //Notify main activity
                runOnUiThread(new Runnable() {
                    public void run() {
                        activity.onConnect();
                    }
                });

                connected = true;
            }
            catch (java.net.UnknownHostException e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Error: Unknown host exception", Toast.LENGTH_SHORT).show();
                    }
                });

                //eturn null;
            }
            catch (java.io.IOException e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Network: No response", Toast.LENGTH_SHORT).show();
                    }
                });

                //eturn null;
            }
            catch(final Throwable e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Error: unknown exception: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            //Perform receive loop
            while(connected) {
                //Receive buffer
                byte[] buffer = new byte[256];

                try {
                    //Get data
                    int len = inputStream.read(buffer, 0, 256);

                    //Process data
                    final Packet packet = processData(buffer, len);

                    if(packet == null) {
                        /*
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(MainActivity.this, "Error: Invalid packet", Toast.LENGTH_SHORT).show();
                            }
                        });*/
                    }
                    else {
                        //Pass the packet to the packet handler
                        runOnUiThread(new Runnable() {
                            public void run() {
                                activity.onPacketReceive(packet);
                            }
                        });
                    }

                }
                catch(java.io.IOException e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "Info: Network disconnected", Toast.LENGTH_SHORT).show();
                        }
                    });

                    connected = false;
                }
            }

            //TCP Connection isn't open if we're here

            //Go back to connect page
            Intent intent = new Intent(MainActivity.this, ConnectPageActivity.class);
            startActivity(intent);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        }

        protected Packet processData(final byte[] data, final int count) {
            //Let's assume that there's one packet of data in the array


            if(data[0] != HEADER_VALUE) {
                //Invalid header
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "Error: Invalid header: " + data[0], Toast.LENGTH_SHORT).show();
                    }
                });

                return null;
            }

            byte type = data[1];

            final int payloadSize = (data[2] << 8) | data[3];

            if(count != (payloadSize + 4)) {
                //We must not have exactly one packet

                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "Error: Size (should be " + (payloadSize + 4)
                                        + ", is " + count + ")", Toast.LENGTH_SHORT).show();
                    }
                });

                //TODO: Deal with this

                return null;
            }

            return new Packet(type, Arrays.copyOfRange(data, 4, payloadSize + 4));
        }
    }
}
