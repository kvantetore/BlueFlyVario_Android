package com.bfv;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.bfv.view.Field;
import com.bfv.view.FieldManager;
import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * This class does all communications with the pebble smart watch.
 */
public class PebbleService {
    //Available Services
    private BFVService varioService;
    private FieldManager fieldManager;
    private Context applicationContext;

    //Constants
    private static final UUID appId = UUID.fromString("4dab81a6-d2fc-458a-992c-7a1f3b96a970");
    private static final long updateFieldsTimerPeriod = 3000L;

    //region Fields

    //State field - either stopped or flying.
    private static final int StateKey = 0;
    private static final int StateStopped = 0;
    private static final int StateFlying = 1;

    //Data fields
    private static final int StateFieldPrefix = 0x1000;

    //endregion

    //Working data
    private PebbleKit.PebbleDataReceiver pebbleHandler;
    private DecimalFormat decimalFormat = new DecimalFormat();
    private Handler handler;
    private Timer updateFieldsTimer;

    public PebbleService(Context ctx, BFVService varioService, FieldManager fieldManager)
    {
        this.applicationContext = ctx;
        this.varioService = varioService;
        this.fieldManager = fieldManager;

        this.handler = new Handler();
    }

    //region Public Interface

    public void start() {
        PebbleKit.startAppOnPebble(applicationContext, appId);
    }

    public void connect() {
        if (pebbleHandler == null) {
            //Attach Handler
            pebbleHandler = new PebbleKit.PebbleDataReceiver(appId) {
                @Override
                public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handlePebbleData(data);
                        }
                    });

                    PebbleKit.sendAckToPebble(context, transactionId);
                }
            };
            PebbleKit.registerReceivedDataHandler(applicationContext, pebbleHandler);
        }

        if (updateFieldsTimer == null) {
            //When the timer is scheduled, run updateFields from the main thread
            TimerTask updateFieldsTask = new TimerTask() {

                @Override
                public void run() {
                    // post a runnable to the handler
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            updateFields();
                        }
                    });
                }
            };

            updateFieldsTimer = new Timer();
            updateFieldsTimer.scheduleAtFixedRate(updateFieldsTask, 0L, updateFieldsTimerPeriod);
        }
    }

    public void disconnect() {
        if (pebbleHandler != null) {
            applicationContext.unregisterReceiver(pebbleHandler);
            pebbleHandler = null;
        }

        if (updateFieldsTimer != null) {
            updateFieldsTimer.cancel();
            updateFieldsTimer = null;
        }
    }

    public void stop() {
        PebbleKit.closeAppOnPebble(applicationContext, appId);
    }

    private void handlePebbleData(PebbleDictionary data) {
        if (data.contains(StateKey))
        {
            handleStateChange(data.getInteger(StateKey));
        }
    }

    //endregion

    private void handleStateChange(long state)
    {
        if (state == StateFlying && varioService.getFlight() == null) {
            varioService.startFlight();
        }
        if (state == StateStopped && varioService.getFlight() != null) {
            varioService.stopFlight();
        }
    }

    private void updateFields() {
        PebbleDictionary data = new PebbleDictionary();

        //Add selected fields
        addFieldValue(data, FieldManager.FIELD_FLIGHT_TIME);
        addFieldValue(data, FieldManager.FIELD_VARIO2);
        addFieldValue(data, FieldManager.FIELD_LOCATION_SPEED);
        addFieldValue(data, FieldManager.FIELD_DAMPED_ALTITUDE);

        PebbleKit.sendDataToPebble(applicationContext, appId, data);
    }

    private void addFieldValue(PebbleDictionary data, int fieldId) {
        //Get Value
        Field fld = fieldManager.findFieldForId(fieldId);
        String value = fieldManager.getValue(fld, new DecimalFormat(fld.getDefaultDecimalFormat()), fld.getDefaultMultiplierIndex());

        Log.i("BFVPebble", "Updating " + fld.getFieldName() + ": " + value);

        //Add value to pebble dict
        data.addString(StateFieldPrefix | fieldId, value);
    }
}
