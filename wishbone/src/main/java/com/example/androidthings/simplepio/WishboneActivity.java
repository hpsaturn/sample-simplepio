/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.simplepio;

import android.app.Activity;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.R.attr.data;

/**
 * Sample usage of the Gpio API that blinks an LED at a fixed interval defined in
 * {@link #INTERVAL_BETWEEN_BLINKS_MS}.
 *
 * Some boards, like Intel Edison, have onboard LEDs linked to specific GPIO pins.
 * The preferred GPIO pin to use on each board is in the {@link BoardDefaults} class.
 *
 */
public class WishboneActivity extends Activity {
    private static final String TAG = WishboneActivity.class.getSimpleName();
    private static final boolean DEBUG = Config.DEBUG;

    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    private Handler mHandler = new Handler();
    private Gpio mLedGpio;
    private SpiDevice mDevice;

    private Wishbone wb;
    private byte[] data = new byte[4];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting BlinkActivity");
        PeripheralManagerService service = new PeripheralManagerService();
        configSPI(service);
        configGPIO(service);


    }

    private void configGPIO(PeripheralManagerService service){
        try {

            String pinName = BoardDefaults.getGPIOForLED();
            mLedGpio = service.openGpio(pinName);
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Log.i(TAG, "Start blinking LED GPIO pin");
            // Post a Runnable that continuously switch the state of the GPIO, blinking the
            // corresponding LED
            mHandler.post(mBlinkRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void configSPI(PeripheralManagerService service){
        try {
            List<String> deviceList = service.getSpiBusList();
            if (deviceList.isEmpty()) {
                Log.i(TAG, "No SPI bus available on this device.");
            } else {
                Log.i(TAG, "List of available devices: " + deviceList);
            }
            mDevice = service.openSpiDevice(BoardDefaults.getSpiBus());
            // Low clock, leading edge transfer
            mDevice.setMode(SpiDevice.MODE3);

            mDevice.setFrequency(18000000);     // 18MHz
            mDevice.setBitsPerWord(8);          // 8 BPW
            mDevice.setBitJustification(false); // MSB first

            wb=new Wishbone(mDevice);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove pending blink Runnable from the handler.
        mHandler.removeCallbacks(mBlinkRunnable);
        // Close the Gpio pin.
        Log.i(TAG, "Closing LED GPIO pin");
        try {
            mLedGpio.close();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        } finally {
            mLedGpio = null;
        }
        if (mDevice != null) {
            try {
                mDevice.close();
                mDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close SPI device", e);
            }
        }
    }

    private Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit Runnable if the GPIO is already closed
            if (mLedGpio == null || wb == null) {
                return;
            }
            try {
                // Toggle the GPIO state
                mLedGpio.setValue(!mLedGpio.getValue());
                Log.d(TAG, "State set to " + mLedGpio.getValue());

                // Reschedule the same runnable in {#INTERVAL_BETWEEN_BLINKS_MS} milliseconds
                mHandler.postDelayed(mBlinkRunnable, INTERVAL_BETWEEN_BLINKS_MS);

                wb.SpiRead((short) (0x3800+(0x00 >> 1)),data,4);
                if(DEBUG)Log.d(TAG,"UVData  :"+Arrays.toString(data));
                if(DEBUG)Log.d(TAG,"UVSensor:"+ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat());

            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };
}
