package com.abdulkarimalbaik.dev.alarmsystem;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.abdulkarimalbaik.dev.alarmsystem.Helper.NotificationHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1111;
    private static final int MESSAGE_RECEIVED = 1;

    TextView txtFlame , txtSmoke , txtWater , txtSwitch , txtBluetooth;
    Switch switchSys;
    Button btnSend;

    BluetoothAdapter mBluetoothAdapter;  //Adapter to using Bluetooth Service
    BluetoothSocket mmSocket;  //endpoint for communication between two machines  (LIKE channel to send and receive data)
    BluetoothDevice mmDevice;  //Device are connected (arduino device)
    OutputStream mmOutputStream;  //This object for Sending data
    InputStream mmInputStream;  //This object for Receive data
    Thread workerThread;    //Thread for real-time results
    volatile boolean stopWorker;   //For check if System is On or OFF , default status of system is stop (true)

    boolean isSent = false;   //For check if notification is sent FOR ONE TIME in this cases (fire & smoke , water)
    byte[] packetBytes;  //Packet of bytes in every sending

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Init views
        txtFlame = (TextView)findViewById(R.id.txt_flame);
        txtSmoke = (TextView)findViewById(R.id.txt_smoke);
        txtWater = (TextView)findViewById(R.id.txt_water);
        txtSwitch = (TextView)findViewById(R.id.txt_switch);
        switchSys = (Switch)findViewById(R.id.swt_sys); 
        btnSend = (Button)findViewById(R.id.btn_send);
        txtBluetooth = (TextView)findViewById(R.id.txt_bluetooth);
        

        //Check if user granted to using Bluetooth & Bluetooth_Admin services
        //user didn't grant using this services
        if (ActivityCompat.checkSelfPermission(this , Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this , Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED){

            //if device is Marshmallow  =>  Request to using this services
            if (Build.VERSION.SDK_INT >= 23)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH , Manifest.permission.BLUETOOTH_ADMIN} , REQUEST_PERMISSION);
        }
        //user granted using this services
        else{
            Toast.makeText(this, "Welcom", Toast.LENGTH_SHORT).show();
        }

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //If system is ON
                if (switchSys.isChecked()){

                    //Send data to arduino
                    sendData();
                }
                //If system is OFF
                else {
                    Toast.makeText(MainActivity.this, "System is close !", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        
        switchSys.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                //If user opened the system
                if (isChecked){

                    txtSwitch.setText("Open");
                    txtBluetooth.setText("Connecting...");
                    stopWorker = false;  //System isn't stop => this mean the system in work status now
                    findBT();

                }
                //If user closed the system
                else {

                    txtSwitch.setText("Close");
                    Toast.makeText(MainActivity.this, "System is closed !", Toast.LENGTH_SHORT).show();
                    closeBT();
                }
            }
        });

    }

    //Sending data to arduino
    private void sendData() {

        if (mmOutputStream != null){

            String msg = "1";
            try {
                mmOutputStream.write(msg.getBytes());
                Toast.makeText(this, "Data is send !", Toast.LENGTH_SHORT).show();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else
            Toast.makeText(this, "the device isn't connected !", Toast.LENGTH_SHORT).show();


    }

    //Finding devices by bluetooth
    private void findBT() {

        //Get the default local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //If device doesn't support bluetooth service
        if(mBluetoothAdapter == null) {

            txtBluetooth.setText("The bluetooth isn't supported");
        }
        else {

            //If bluetooth is OFF
            if(!mBluetoothAdapter.isEnabled()) {

                //Send request to android system to enable bluetooth
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);
            }
            else {

                //Set of devices are shown by bluetooth
                Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                //If there any devices
                if(pairedDevices.size() > 0)
                {
                    for(BluetoothDevice device : pairedDevices)
                    {
                        //If device is HC-05 => connect it and open communication with it
                        if(device.getName().equals("HC-05"))
                        {
                            mmDevice = mBluetoothAdapter.getRemoteDevice("00:13:EF:00:0C:2B");
                            Toast.makeText(MainActivity.this, "System is opened !", Toast.LENGTH_SHORT).show();
                            openBT();
                            break;
                        }
                    }
                }
                else
                    //No devices are close now
                    txtBluetooth.setText("No Devices are detected");
            }
        }

    }

    //Open communication with the device (arduino)
    private void openBT(){

        //Standard SerialPortService ID (it emulates a serial cable communication)
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        try {
            //Create socket (secure ready to start a secure outgoing connection to this remote device using SDP lookup of uuid)
            mmSocket = createBluetoothSocket(uuid);
            //Connect with arduino
            mmSocket.connect();
            //Get the output stream associated with this socket
            mmOutputStream = mmSocket.getOutputStream();
            //Get the Input stream associated with this socket
            mmInputStream = mmSocket.getInputStream();

            txtBluetooth.setText("System is connected");
            receiveBluetoothData();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Close communication with the device (arduino)
    private void closeBT() {

        //The system is close now
        if(mmSocket != null && mmOutputStream != null && mmInputStream != null){

            stopWorker = true;

            try {

                //close data channel between smartPhone and arduino
                mmOutputStream.close();
                mmInputStream.close();
                mmSocket.close();
                txtBluetooth.setText("System is disconnected");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else
            txtBluetooth.setText("System is disconnected");

    }

    private void receiveBluetoothData(){

        //The system is work now
        stopWorker = false;

        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        //Check number of bytes from arduino
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            //Create packet to bytes , size of packet is size of bytes
                            packetBytes = new byte[bytesAvailable];
                            //fill the packet by bytes
                            mmInputStream.read(packetBytes);

                            final String data = new String(packetBytes);
                            data.trim();

                            final String[] dataSplit = splitString(data);

                            //If data is empty => don't change status of system
                            if (dataSplit == null ||dataSplit[0] == null || dataSplit[1] == null || dataSplit[2] == null)
                                txtBluetooth.setText("Connected");
                            else
                                changeStatusSystem(Integer.valueOf(dataSplit[0])
                                    ,Integer.valueOf(dataSplit[1])
                                    ,Integer.valueOf(dataSplit[2]));

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    txtFlame.setText(dataSplit[0]);
                                    txtSmoke.setText(dataSplit[1]);
                                    txtWater.setText(dataSplit[2]);

                                }
                            });
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    //Change status of system by data received from arduino
    private void changeStatusSystem(int flame , int smoke , int water) {

        if (flame == 0 && smoke < 320){   //No fire & No smoke
            txtBluetooth.setText("COOL");
            isSent = false;
        }
        else if (flame == 0 && smoke >= 320){  //No fire & Yes smoke
            txtBluetooth.setText("SMOKE");
            isSent = false;
        }
        else if (flame >= 1 && smoke < 320){  //Yes fire & No smoke
            txtBluetooth.setText("FIRE");
            isSent = false;
        }
        else if (flame >= 1 && smoke >= 320){  //Yes fire & Yes smoke

            if (water < 150){   //No water , send notification
                txtBluetooth.setText("FIRE & SMOKE");
                sendNotificationToUser("FIRE & SMOKE");

            }
            else {     //Yes water , send notification
                txtBluetooth.setText("Water PUMP");
                sendNotificationToUser("Water PUMP");
            }
        }

    }

    //Sending notification by system
    private void sendNotificationToUser(String alarm) {

        //If user didn't receive notification for fire&smoke OR water before => notify (Just for one time)
        if (!isSent){

            //Send Notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                sendNotificationAPI26(alarm);
            else
                sendNotification(alarm);

            //user received the notification => prevent any notification until system comeback to COOL_or_SOMKE_or_FIRE status
            isSent = true;
        }
    }

    //Split string to array of stings per ','
    private String[] splitString(String data) {

        int j = 0;
        String[] modifiedData = new String[3];
        String s = "";

        for (int i = 0; i < data.length(); i++) {

            if (data.charAt(i) == ','){

                modifiedData[j] = s;
                j++;
                s = "";
            }
            else
                s += String.valueOf(data.charAt(i));

        }
        modifiedData[2] = s;

        return modifiedData;
    }

    //Create socket between two devices (smartPhone & arduino)
    private BluetoothSocket createBluetoothSocket(UUID uuid) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){  //Device is GINGERBREAD_MR1
            try {
                //Using this method (createInsecureRfcommSocketToServiceRecord) that exists in BluetoothDevice Class
                final Method m = mmDevice.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(mmDevice, uuid);
            }
            catch (Exception e) {

                Toast.makeText(this, e.getMessage() , Toast.LENGTH_SHORT).show();
            }
        }
        return  mmDevice.createRfcommSocketToServiceRecord(uuid);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void sendNotificationAPI26(String data) {

        PendingIntent pendingIntent;   //Message to open alarm system app
        NotificationHelper helper;
        Notification.Builder builder;  //Object Notification

        //Create message
        Intent intent = new Intent(this , MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //if activity is created before => clear alarm system activity and create new activity and make it in top
        pendingIntent = PendingIntent.getActivity(this , 0 , intent ,
                PendingIntent.FLAG_UPDATE_CURRENT); //if the described PendingIntent already exists, then keep it but replace its extra data with what is in this new

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); //set default sound of system
        helper = new NotificationHelper(this);
        builder = helper.getAlarmSystemChannelNotification("Warning !",
                data,
                pendingIntent,
                defaultSoundUri);

        //Show notification
        helper.getManager().notify(new Random().nextInt() , builder.build());

    }

    private void sendNotification(String data) {

        Intent intent = new Intent(this , MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this , 0 , intent ,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_alarm_system)
                .setContentTitle("Warning !")
                .setContentText(data)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(0  , builder.build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode){

            case REQUEST_PERMISSION: {

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED ){

                    Toast.makeText(this, "PERMISSION GRANTED", Toast.LENGTH_SHORT).show();
                }
                else {

                    System.exit(0);
                    Toast.makeText(this, "You can't use this app", Toast.LENGTH_LONG).show();
                }

                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        //Show result of request to enable bluetooth

        //if user enabled bluetooth
        if (requestCode == 0 && resultCode == RESULT_OK){

            //Set of devices are shown by bluetooth
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            //If there any devices
            if(pairedDevices.size() > 0)
            {
                for(BluetoothDevice device : pairedDevices)
                {
                    //If device is HC-05 => connect it and open communication with it
                    if(device.getName().equals("HC-05"))
                    {
                        mmDevice = mBluetoothAdapter.getRemoteDevice("00:13:EF:00:0C:2B");
                        openBT();
                        break;
                    }
                }
            }
            //No devices are close now
            else
                txtBluetooth.setText("No Devices are detected");
        }
        //if user didn't enable bluetooth
        else if (requestCode == 0 && resultCode == RESULT_CANCELED){

            Toast.makeText(this, "Please enable bluetooth !", Toast.LENGTH_SHORT).show();
            txtBluetooth.setText("Bluetooth is off");
        }
    }
}

