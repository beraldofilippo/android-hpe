package com.beraldo.hpe.utils;

import android.content.Context;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class XMLReader {
    private static String TAG = "XMLReader";


    public static float[] loadIntrinsicParams(Context ctx) {
        float[] result = new float[4];
        XmlPullParserFactory pullParserFactory;
        XmlPullParser parser;

        // Open config file
        FileReader in_s = null;
        try {
            in_s = new FileReader(FileUtils.getPreference(ctx, FileUtils.PARAMS_DIR_PREFS_NAME) + "/intrinsics.xml");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Couldn't find the config file for intrinsics! Returning default array.");
            return result;
        }

        // Open parser
        try {
            pullParserFactory = XmlPullParserFactory.newInstance();
            parser = pullParserFactory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in_s);

            /*
            * ###### Setting up values suitable for Huawei P9 Lite
            * */
            /*mCameraIntrinsics[0] = 502.129f;
            mCameraIntrinsics[1] = 501.849f;
            mCameraIntrinsics[2] = 320.297f;
            mCameraIntrinsics[3] = 245.988f;*/

            /*<?xml version="1.0" encoding="UTF-8"?>
            <!-- [f_x, f_y, c_x, c_y, s] -->
            <intrinsics>
                <fx>xx.xxxxxx</fx>
                <fy>xx.xxxxxx</fy>
                <cx>xx.xxxxxx</cx>
                <cy>xx.xxxxxx</cy>
            </intrinsics>
            */
            int eventType = 0;
            try {
                eventType = parser.getEventType();

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String name = null;
                    switch (eventType) {
                        case XmlPullParser.START_DOCUMENT:
                            break;
                        case XmlPullParser.START_TAG:
                            name = parser.getName();
                            if (name.equalsIgnoreCase("fx")) {
                                result[0] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("fy")) {
                                result[1] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("cx")) {
                                result[2] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("cy")) {
                                result[3] = Float.parseFloat(parser.nextText());
                            } else
                                break;
                /*case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("product") && currentProduct != null){
                        products.add(currentProduct);
                    }*/
                    }
                    eventType = parser.next();
                }
            } catch (XmlPullParserException e) {
                Log.d(TAG, "Couldn't parse correctly the file of intrinsics params, error message " + e.getMessage());
                e.printStackTrace();
                return result;
            } catch (IOException e) {
                Log.d(TAG, "Couldn't parse correctly the file of intrinsics params, error message " + e.getMessage());
                e.printStackTrace();
                return result;
            }
        } catch (XmlPullParserException e) {
            Log.d(TAG, "Couldn't parse correctly the file of intrinsics params, error message " + e.getMessage());
            e.printStackTrace();
            return result;
        }

        return result;
    }

    public static float[] loadDistortionParams(Context ctx) {

        float[] result = new float[5];
        XmlPullParserFactory pullParserFactory;
        XmlPullParser parser;

        // Open config file
        FileReader in_s = null;
        try {
            Log.d(TAG, FileUtils.getPreference(ctx, FileUtils.PARAMS_DIR_PREFS_NAME) + "/distortion.xml");
            in_s = new FileReader(FileUtils.getPreference(ctx, FileUtils.PARAMS_DIR_PREFS_NAME) + "/distortion.xml");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Couldn't find the config file for distorions! Returning default array.");
            return result;
        }

        // Open parser
        try {
            pullParserFactory = XmlPullParserFactory.newInstance();
            parser = pullParserFactory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in_s);

            /*
            * ###### Setting up values suitable for Huawei P9 Lite
            * */
            /*mCameraDistortions[0] = -0.010f; // k1
            mCameraDistortions[1] = 1.303f; // k2
            mCameraDistortions[2] = 0.000f; // p1
            mCameraDistortions[3] = 0.000f; // p2
            mCameraDistortions[4] = -5.959f; // k3*/
            /*<?xml version="1.0" encoding="UTF-8"?>
            <!-- [k_1, k_2, p_1, p_2, k_3] -->
            <distortion>
                <k1>xx.xxxxxx</k1>
                <k2>xx.xxxxxx</k2>
                <k3>xx.xxxxxx</k3>
                <p1>xx.xxxxxx</p1>
                <p2>xx.xxxxxx</p2>
            </distortion>
            */
            int eventType = 0;
            try {
                eventType = parser.getEventType();

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String name = null;
                    switch (eventType) {
                        case XmlPullParser.START_DOCUMENT:
                            break;
                        case XmlPullParser.START_TAG:
                            name = parser.getName();
                            if (name.equalsIgnoreCase("k1")) {
                                result[0] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("k2")) {
                                result[1] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("p1")) {
                                result[2] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("p2")) {
                                result[3] = Float.parseFloat(parser.nextText());
                            } else if (name.equalsIgnoreCase("k3")) {
                                result[4] = Float.parseFloat(parser.nextText());
                            } else
                                break;
                /*case XmlPullParser.END_TAG:
                    name = parser.getName();
                    if (name.equalsIgnoreCase("product") && currentProduct != null){
                        products.add(currentProduct);
                    }*/
                    }
                    eventType = parser.next();
                }
            } catch (XmlPullParserException e) {
                Log.d(TAG, "Couldn't parse correctly the file of intrinsics params, error message " + e.getMessage());
                e.printStackTrace();
                return result;
            } catch (IOException e) {
                Log.d(TAG, "Couldn't parse correctly the file of intrinsics params, error message " + e.getMessage());
                e.printStackTrace();
                return result;
            }
        } catch (XmlPullParserException e) {
            Log.d(TAG, "Couldn't parse correctly the file of intrinsics params, error message " + e.getMessage());
            e.printStackTrace();
            return result;
        }
        return result;
    }
}
