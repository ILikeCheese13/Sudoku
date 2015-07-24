package com.jacob.sudoku;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import static org.opencv.core.Core.flip;
import static org.opencv.core.Core.transpose;

public class SudokuActivity extends Activity {
    public static final String PACKAGE_NAME = "com.datumdroid.android.ocr.simple";
    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/SudokuActivity/";

    // You should have the trained data file in assets folder
    // You can get them at:
    // http://code.google.com/p/tesseract-ocr/downloads/list
    public static final String lang = "eng";

    private static final String TAG = "SudokuActivity.java";

    protected Button _button;
    // protected ImageView _image;
    protected EditText _field;
    protected String _path;
    protected boolean _taken;

    protected static final String PHOTO_TAKEN = "photo_taken";

    @Override
    public void onCreate(Bundle savedInstanceState) {

        String[] paths = new String[]{DATA_PATH, DATA_PATH + "tessdata/"};
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }

        }

        // lang.traineddata file with the app (in assets folder)
        // You can get them at:
        // http://code.google.com/p/tesseract-ocr/downloads/list
        // This area needs work and optimization
        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {

                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                //GZIPInputStream gin = new GZIPInputStream(in);
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                //gin.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sudoku);

        // _image = (ImageView) findViewById(R.id.image);
        _field = (EditText) findViewById(R.id.field);
        _button = (Button) findViewById(R.id.button);
        _button.setOnClickListener(new ButtonClickHandler());

        _path = DATA_PATH + "/ocr.jpg";
    }

    public class ButtonClickHandler implements View.OnClickListener {
        public void onClick(View view) {
            Log.v(TAG, "Starting Camera app");
            startCameraActivity();
        }
    }

    // Simple android photo capture:
    // http://labs.makemachine.net/2010/03/simple-android-photo-capture/

    protected void startCameraActivity() {
        File file = new File(_path);
        Uri outputFileUri = Uri.fromFile(file);

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "resultCode: " + resultCode);

        if (resultCode == -1) {
            onPhotoTaken();
        } else {
            Log.v(TAG, "User cancelled");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(SudokuActivity.PHOTO_TAKEN, _taken);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(TAG, "onRestoreInstanceState()");
        if (savedInstanceState.getBoolean(SudokuActivity.PHOTO_TAKEN)) {
            onPhotoTaken();
        }
    }

    protected void onPhotoTaken() {
        _taken = true;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        Bitmap bitmap = BitmapFactory.decodeFile(_path, options);
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);;
        Mat tmp = new Mat (bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
        // creating a temp object for the pre-processing of the image taken

        try
        {
            Mat outerBox = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC1);
            // Creating an object which will hold the outer box of the puzzle

            Utils.bitmapToMat(bitmap, tmp);
            // transforming the picture taken into a Mat
            Imgproc.cvtColor(tmp, tmp, Imgproc.COLOR_RGB2GRAY);
            // Turning the initial image into gray scale
            org.opencv.core.Size s = new Size(11,11);
            Imgproc.GaussianBlur(tmp,tmp,s, 0);
            // Blurring the Image a little, this smooths the noise and makes extracting
            // Grid lines easier
            Imgproc.adaptiveThreshold(tmp, tmp, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 5, 2);
            // Applying an adaptive threshold to the image, this smooths out the varying
            // Illumination levels. This function calculates a threshold level on several
            // Small windows, this keeps illumination independent
            // Calculates mean over a 5x5 windows and subtracts 2 from the mean, this is
            // the threshold level for every pixel
            org.opencv.core.Core.bitwise_not(tmp, tmp);
            // Inverts every bit of an array, since we are interested in the borders,
            // this makes the borders of the puzzle white (along with other noise)
            Mat kernel = new Mat(3, 3, CvType.CV_8UC1);
            int row = 0, col = 0;
            kernel.put(row, col, 0, 1, 0, 1, 1, 1, 0, 1, 0);
            // Creates a "plus" shaped matrix
            Imgproc.dilate(tmp, outerBox, kernel);
            // Dilating the image to fill up any disconnected lines that may have occured
            // During the adaptive threshold

            Utils.matToBitmap(outerBox, bitmap);
            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            Imgproc.findContours(outerBox, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            // Find all contours of the image we have
            Utils.matToBitmap(outerBox, bitmap);
            double maxArea=0;
            Iterator<MatOfPoint> each = contours.iterator();
            MatOfPoint2f biggest = new MatOfPoint2f();
            MatOfPoint mContours = new MatOfPoint();
            while (each.hasNext())
            {
                MatOfPoint wrapper = each.next();
                MatOfPoint2f wrapper1 = new MatOfPoint2f(wrapper.toArray());
                double area = Imgproc.contourArea(wrapper);
                MatOfPoint2f approx = new MatOfPoint2f();
                Log.e(TAG, "area " + area);
                if (area > 100) {
                    double peri = Imgproc.arcLength(wrapper1, true);
                    Imgproc.approxPolyDP(wrapper1, approx, (0.02*peri), true);
                    if (area > maxArea && approx.toArray().length == 4)
                    { // If a bigger max area is found, and the shape has 4 sides
                        biggest = approx;
                        maxArea = area;
                        Log.e(TAG, "Max Area " + area);
                    }
                }
            }
            // Find max contour area which is finding where the puzzle is

            biggest.convertTo(mContours, CvType.CV_32S);
            List<MatOfPoint> contourTemp = new ArrayList<>();
            contourTemp.add(mContours);

            Point destP1 = new Point (0,0);
            Point destP2 = new Point (0,449);
            Point destP3 = new Point (449,449);
            Point destP4 = new Point (449,0);
            // Setting new puzzle size as 450x450
            List<Point> dest = new ArrayList<Point>();
            dest.add(destP1);
            dest.add(destP2);
            dest.add(destP3);
            dest.add(destP4);
            Mat h = Converters.vector_Point2f_to_Mat(dest);
            Mat persepctiveTrans = Imgproc.getPerspectiveTransform(biggest, h);
            Imgproc.warpPerspective(tmp, tmp, persepctiveTrans, new Size(449, 449));
            transpose(tmp, tmp);
            flip(tmp, tmp, 1);
            output = Bitmap.createBitmap(tmp.width(), tmp.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(tmp, output);
            Imgproc.drawContours(outerBox, contourTemp, -1, new Scalar(255, 255, 255));
            Utils.matToBitmap(outerBox, bitmap);
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        try {
            /*
            ExifInterface exif = new ExifInterface(_path);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            Log.v(TAG, "Orient: " + exifOrientation);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }
*/
            Log.v(TAG, "Rotation: " + 90);
            int rotate = 90;
            if (rotate != 0) {

                // Getting width & height of the given image.
                int w = output.getWidth();
                int h = output.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                output = Bitmap.createBitmap(output, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            output = output.copy(Bitmap.Config.ARGB_8888, true);

        } catch (Exception e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
        }

        // _image.setImageBitmap( bitmap );

        // Our image 'outout' at this point is now rotated correctly and is 450x450 pixels
        // this means each 3x3 sub square is 150x150 pixels, this then means that every square
        // is 50x50 pizels
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(tmp.clone(), contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        // Find all contours of the image we have
        Iterator<MatOfPoint> each = contours.iterator();
        MatOfPoint2f biggest = new MatOfPoint2f();
        MatOfPoint mContours = new MatOfPoint();
        List<MatOfPoint> squares = new ArrayList<MatOfPoint>();
        int[][] corners = new int[9][4];
        int counter = 0;
        Rect example;
        // TODO: Testing with a regular sudoku puzzle, no more missing box line crap
        while (each.hasNext())
        {
            MatOfPoint wrapper = each.next();
            MatOfPoint2f wrapper1 = new MatOfPoint2f(wrapper.toArray());
            double area = Imgproc.contourArea(wrapper);
            MatOfPoint2f approx = new MatOfPoint2f();
            if (area > 2000) {
                double peri = Imgproc.arcLength(wrapper1, true);
                Imgproc.approxPolyDP(wrapper1, approx, (0.02*peri), true);
                if ((approx.toArray().length == 4) && (area < 20000))
                { // If the shape has 4 sides, and is approx what we want
                    squares.add(wrapper);
                    example = Imgproc.boundingRect(wrapper);
                    corners[counter][0] = example.x;
                    corners[counter][1] = example.y;
                    corners[counter][2] = example.width;
                    corners[counter][3] = example.height;
                    Log.e(TAG, "Area " + area);
                    counter ++;
                }
            }
        }
        // This while loop is finding the 9 sub boxes in the puzzle
        // At this point there should be 9 elements in squares
        // The elements are not sorted at this point, can be in a random order

        for (int l=0;l<corners.length-1;l++)
        {
            int[][] cornersTmp = new int[1][4];
            if ((corners[l][0]+corners[l][1]) < (corners[0][0]+corners[0][1]))
            { // If x+y < value stored at [0], [0] is assumed to be min point
                cornersTmp[0] = corners[l];
                corners[l] = corners[0];
                corners[0] = cornersTmp[0];
            }
            if ((corners[l][0]+corners[l][1]) > (corners[corners.length-1][0]+corners[corners.length-1][1]))
            { // If x+y > value stored at [8], [8] is assumed to be max point
                cornersTmp[0] = corners[l];
                corners[l] = corners[corners.length-1];
                corners[corners.length-1] = cornersTmp[0];
            }
        }
        //Looping to find the bottom left to top right corners

        for (int l=1;l<corners.length-1;l++)
        {
            int[][] cornersTmp = new int[1][4];
            if ((corners[l][0] >= 140 && corners[l][0] <= 160) && (corners[l][1] >= 0 &&
                corners[l][1] <= 20)){
                cornersTmp[0] = corners[1];
                corners[1] = corners[l];
                corners[l] = cornersTmp[0];
            } else if ((corners[l][0] >= 280 && corners[l][0] <= 300) && (corners[l][1] >= 0 &&
                    corners[l][1] <= 20)){
                cornersTmp[0] = corners[2];
                corners[2] = corners[l];
                corners[l] = cornersTmp[0];
            } else if ((corners[l][0] >= 0 && corners[l][0] <= 20) && (corners[l][1] >= 140 &&
                    corners[l][1] <= 160)){
                cornersTmp[0] = corners[3];
                corners[3] = corners[l];
                corners[l] = cornersTmp[0];
            } else if ((corners[l][0] >= 140 && corners[l][0] <= 160) && (corners[l][1] >= 140 &&
                    corners[l][1] <= 160)){
                cornersTmp[0] = corners[4];
                corners[4] = corners[l];
                corners[l] = cornersTmp[0];
            } else if ((corners[l][0] >= 280 && corners[l][0] <= 300) && (corners[l][1] >= 140 &&
                    corners[l][1] <= 160)){
                cornersTmp[0] = corners[5];
                corners[5] = corners[l];
                corners[l] = cornersTmp[0];
            } else if ((corners[l][0] >= 0 && corners[l][0] <= 20) && (corners[l][1] >= 280 &&
                    corners[l][1] <= 300)){
                cornersTmp[0] = corners[6];
                corners[6] = corners[l];
                corners[l] = cornersTmp[0];
            } else if ((corners[l][0] >= 140 && corners[l][0] <= 160) && (corners[l][1] >= 280 &&
                    corners[l][1] <= 300)){
                cornersTmp[0] = corners[7];
                corners[7] = corners[l];
                corners[l] = cornersTmp[0];
            }
        }
        // Sorting the array to look like the following
        /*   +-----+-----+-----+
             |     |     |     |
             | [0] | [3] | [6] |
             |     |     |     |
             +-----+-----+-----+
             |     |     |     |
             | [1] | [4] | [7] |
             |     |     |     |
             +-----+-----+-----+
             |     |     |     |
             | [2] | [5] | [8] |
             |     |     |     |
             +-----+-----+-----+
        */

        biggest.convertTo(mContours, CvType.CV_32S);
        List<MatOfPoint> contourTemp = new ArrayList<>();
        contourTemp.add(mContours);
        //Imgproc.drawContours(tmp, squares, -1, new Scalar(255, 255, 255));
        Mat box;
        String[][] boxes = new String[9][9];
        Rect boxCord;
        int k = 0;
        for (int i=0; i < corners.length;i++) {
            box = tmp.submat(corners[i][0], corners[i][0] + corners[i][2], corners[i][1], corners[i][1] + corners[i][3]);
            // Above calculation is (top corner x, top corner x + width, top corner y, top corner y + height)
            // This gives me a box with 9 sub boxes
            Bitmap tmpBox = Bitmap.createBitmap(box.width(), box.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(box, tmpBox);
            List<MatOfPoint> contoursloop = new ArrayList<MatOfPoint>();
            Imgproc.findContours(box.clone(), contoursloop, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            Iterator<MatOfPoint> eachbox = contoursloop.iterator();
            Log.e(TAG, "NEW_IT");
            while (eachbox.hasNext())
            {
                MatOfPoint wrapper = eachbox.next();
                MatOfPoint2f wrapper1 = new MatOfPoint2f(wrapper.toArray());
                double area = Imgproc.contourArea(wrapper);
                MatOfPoint2f approx = new MatOfPoint2f();
                Log.e(TAG, "AREA: " + area);
                if (area > 2000) {
                    double peri = Imgproc.arcLength(wrapper1, true);
                    Imgproc.approxPolyDP(wrapper1, approx, (0.02*peri), true);
                    Log.e(TAG, "SIDES: " + approx.toArray().length);
                    if ((approx.toArray().length == 4))
                    { // Case of where only the middle box can be found
                        //TODO Add cases for when more than middle box found
                        //TODO Refactor code into functions
                        //TODO ADD COMMENTS
                        boxCord = Imgproc.boundingRect(wrapper);
                        Mat[] numbers = new Mat[9];
                        numbers[0] = box.submat(4, boxCord.y-4, 4, boxCord.x-4);
                        numbers[1] = box.submat(4, boxCord.y-4, boxCord.x+4, 2*boxCord.x-4);
                        numbers[2] = box.submat(4, boxCord.y-4, 2*boxCord.x+8, box.width()-8);
                        numbers[3] = box.submat(boxCord.y+4, 2*boxCord.y-4, 4, boxCord.x-4);
                        numbers[4] = box.submat(boxCord.y+4, 2*boxCord.y-4, boxCord.x+4, 2*boxCord.x-4);
                        numbers[5] = box.submat(boxCord.y+4, 2*boxCord.y-4, 2*boxCord.x+8, box.width()-4);
                        numbers[6] = box.submat(2*boxCord.y+4, box.height()-4, 4, boxCord.x-4);
                        numbers[7] = box.submat(2*boxCord.y+4, box.height()-4, boxCord.x+5, 2*boxCord.x-8);
                        numbers[8] = box.submat(2*boxCord.y+4, box.height()-4, 2*boxCord.x+8, box.width()-1-4);
                        Bitmap[] numBit = new Bitmap[9];
                        for (int j=0; j<numbers.length;j++)
                        {
                            numBit[j] = Bitmap.createBitmap(numbers[j].width(), numbers[j].height(), Bitmap.Config.ARGB_8888);
                            Utils.matToBitmap(numbers[j], numBit[j]);
                            TessBaseAPI baseApi = new TessBaseAPI();
                            baseApi.setDebug(true);
                            baseApi.init(DATA_PATH, lang);
                            baseApi.setImage(numBit[j]);
                            String recognizedText = baseApi.getUTF8Text();

                            baseApi.end();

                            // You now have the text in recognizedText var, you can do anything with it.
                            // We will display a stripped out trimmed alpha-numeric version of it (if lang is eng)
                            // so that garbage doesn't make it to the display.

                            Log.v(TAG, "OCRED TEXT: " + recognizedText);

                            if (lang.equalsIgnoreCase("eng")) {
                                recognizedText = recognizedText.replaceAll("[^0-9]+", " ");
                            }
                            recognizedText = recognizedText.trim();
                            if (recognizedText.length() == 1 && !recognizedText.equals(""))
                                boxes[k][j] = recognizedText;
                            else
                                boxes[k][j] = "0";
                            Log.e(TAG, "TRIMMED TEXT of (i,j,k) " + i + j + k + " " + recognizedText);
                        }
                        k +=1;
                    }
                }
            }
        }

        String puzStr = "";
        int seed=0;
        for (int i=0; i<3;i++) {
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[0][j];
            }
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[3][j];
            }
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[6][j];
            }
            puzStr = puzStr + "\r\n";
            seed = seed + 3;
        }
        seed=0;
        for (int i=0; i<3;i++) {
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[1][j];
            }
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[4][j];
            }
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[7][j];
            }
            puzStr = puzStr + "\r\n";
            seed = seed + 3;
        }
        seed=0;
        for (int i=0; i<3;i++) {
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[2][j];
            }
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[5][j];
            }
            for (int j=seed;j<seed+3;j++){
                puzStr = puzStr + boxes[8][j];
            }
            puzStr = puzStr + "\r\n";
            seed = seed + 3;
        }


        if (puzStr.length() != 0) {
            _field.setText(_field.getText().toString().length() == 0 ? puzStr : _field.getText() + " " + puzStr);
            _field.setSelection(_field.getText().toString().length());
            Log.v(TAG, "Puzzle String: \n\r" + puzStr);
        }

        // Cycle done.
    }

    // www.Gaut.am was here
    // Thanks for reading!
}
