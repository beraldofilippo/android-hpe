package com.beraldo.hpe.utils;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * This class creates a builder for the xml document with the results.
 * XML files I want to build are of the type:
 * <selfear2 mode="0" time_performance="XXXXXXX.XX">
 * <result>
 *     <timestamp>xxxxxxxxxxxx</timestamp>
 *     <yaw>XX.XX</yaw>
 *     <pitch>XXX.XX</pitch>
 *     <roll>XXX.XX</roll>
 * </result>
 * </selfear2>
 */
public class XMLWriter {
    private static String TAG = "XMLWriter";
    private static DocumentBuilder mInstance = null;

    private static void init() {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            mInstance = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public static Document newDocument(int m) {
        if(mInstance == null) {
            init();
        }

        Document document = mInstance.newDocument();
        Element selfear2 = document.createElement("selfear2");
        selfear2.setAttribute("mode", ""+m);
        document.appendChild(selfear2);

        return document;
    }

    public static synchronized void addResult(Document d, double tstamp, double y, double p, double r) {
        Element result = d.createElement("result");
        d.getDocumentElement().appendChild(result);

        Element ts = d.createElement("timestamp");
        result.appendChild(ts);
        ts.appendChild(d.createTextNode(""+tstamp));

        Element yaw = d.createElement("yaw");
        result.appendChild(yaw);
        yaw.appendChild(d.createTextNode(""+y));

        Element pitch = d.createElement("pitch");
        result.appendChild(pitch);
        pitch.appendChild(d.createTextNode(""+p));

        Element roll = d.createElement("roll");
        result.appendChild(roll);
        roll.appendChild(d.createTextNode(""+r));
    }

    public static void saveDocumentToFile(Context ctx, Document d, String fname) {
        File myDir = new File(FileUtils.getPreference(ctx, FileUtils.DETECTIONS_DIR_PREFS_NAME));

        if(!myDir.exists()) { // Ensure the directory exists, probably not needed anymore
            if (!myDir.mkdirs()) {
                Log.d(TAG, "Make directory failed, not saving the file.");
            }
        }
        final File file = new File(myDir, fname);
        if (file.exists()) file.delete();
        try {
            Source source = new DOMSource(d);
            Result result = new StreamResult(file);
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(source, result);

            Log.d(TAG, String.format("Saved detection xml to " + file.getAbsolutePath()));
        } catch (final Exception e) {
            Log.e(TAG, "Exception!", e);
        }
    }

    public static void addTimePerformance(Document d, double t) {
        d.getDocumentElement().setAttribute("time_performance", ""+t);
    }
}
