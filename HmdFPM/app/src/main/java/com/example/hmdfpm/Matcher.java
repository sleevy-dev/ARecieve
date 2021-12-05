package com.example.hmdfpm;

import java.util.ArrayList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;

class Matcher {
    private final float targetThreshold = 35.0f;
    private final int hessianThreshold = 400;

    private Mat imgObject;

    private MatOfKeyPoint keypointsObject = new MatOfKeyPoint();

    private Mat descriptorsObject = new Mat();

    public boolean isReady = false;

    public float[] centerOfMatches = new float[2];

    private ORB detector;

    public void setOriginImg(Mat image){
        imgObject = image;

        if(imgObject.empty()){
            System.err.println("Cannot read images!");
            return;
        }

        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGBA2GRAY);

        detector = setupORB(hessianThreshold, imgObject, keypointsObject, descriptorsObject);
        isReady = true;
    }

    public Mat run(Mat imgScene) {
        if(!isReady)
            return null;

        try{
            detector = ORB.create(hessianThreshold);

            Mat tempMat = new Mat();
            Imgproc.cvtColor(imgScene, tempMat, Imgproc.COLOR_RGBA2GRAY);

            MatOfKeyPoint  keypointsScene = new MatOfKeyPoint();
            Mat descriptorsScene = new Mat();

            detector = setupORB(hessianThreshold, tempMat,keypointsScene, descriptorsScene);

            //-- Step 2: Matching descriptor vectors with a FLANN based matcher
            // Since SURF is a floating-point descriptor NORM_L2 is used
            DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
            List<MatOfDMatch> knnMatches = new ArrayList<>();
            matcher.knnMatch(descriptorsObject, descriptorsScene, knnMatches, 2);

            //-- Filter matches using the Lowe's ratio test
            float ratioThresh = 0.75f;
            List<DMatch> listOfGoodMatches = new ArrayList<>();
            for (int i = 0; i < knnMatches.size(); i++) {
                if (knnMatches.get(i).rows() > 1) {
                    DMatch[] matches = knnMatches.get(i).toArray();
                    if (matches[0].distance < ratioThresh * matches[1].distance) {
                        listOfGoodMatches.add(matches[0]);
                    }
                }
            }

            MatOfDMatch goodMatches = new MatOfDMatch();
            goodMatches.fromList(listOfGoodMatches);

            List<Point> obj = new ArrayList<>();
            List<Point> scene = new ArrayList<>();

            List<KeyPoint> listOfKeypointsObject = keypointsObject.toList();
            List<KeyPoint> listOfKeypointsScene = keypointsScene.toList();

            float distances = 0.0f;

            for (int i = 0; i < listOfGoodMatches.size(); i++) {
                //-- Get the keypoints from the good matches
                distances += listOfGoodMatches.get(i).distance;

                obj.add(listOfKeypointsObject.get(listOfGoodMatches.get(i).queryIdx).pt);
                scene.add(listOfKeypointsScene.get(listOfGoodMatches.get(i).trainIdx).pt);
            }

            distances = distances/listOfGoodMatches.size();

            if (distances > targetThreshold) {
                return null;
            }

            MatOfPoint2f objMat = new MatOfPoint2f(), sceneMat = new MatOfPoint2f();
            objMat.fromList(obj);
            sceneMat.fromList(scene);
            double ransacReprojThreshold = 3.0;
            Mat H = Calib3d.findHomography( objMat, sceneMat, Calib3d.RANSAC, ransacReprojThreshold );
            //-- Get the corners from the image_1 ( the object to be "detected" )
            Mat objCorners = new Mat(4, 1, CvType.CV_32FC2), sceneCorners = new Mat();
            float[] objCornersData = new float[(int) (objCorners.total() * objCorners.channels())];

            objCorners.get(0, 0, objCornersData);
            objCornersData[0] = 0;
            objCornersData[1] = 0;
            objCornersData[2] = imgObject.cols();
            objCornersData[3] = 0;
            objCornersData[4] = imgObject.cols();
            objCornersData[5] = imgObject.rows();
            objCornersData[6] = 0;
            objCornersData[7] = imgObject.rows();
            objCorners.put(0, 0, objCornersData);
            Core.perspectiveTransform(objCorners, sceneCorners, H);
            float[] sceneCornersData = new float[(int) (sceneCorners.total() * sceneCorners.channels())];
            sceneCorners.get(0, 0, sceneCornersData);

            Imgproc.line(imgScene, new Point(sceneCornersData[0] , sceneCornersData[1]),
                    new Point(sceneCornersData[2] , sceneCornersData[3]), new Scalar(0, 255, 0), 4);
            Imgproc.line(imgScene, new Point(sceneCornersData[2] , sceneCornersData[3]),
                    new Point(sceneCornersData[4] , sceneCornersData[5]), new Scalar(0, 255, 0), 4);
            Imgproc.line(imgScene, new Point(sceneCornersData[4], sceneCornersData[5]),
                    new Point(sceneCornersData[6] , sceneCornersData[7]), new Scalar(0, 255, 0), 4);
            Imgproc.line(imgScene, new Point(sceneCornersData[6] , sceneCornersData[7]),
                    new Point(sceneCornersData[0] , sceneCornersData[1]), new Scalar(0, 255, 0), 4);

            return imgScene;
        } catch (CvException e){
            return null;
        }
    }

    private ORB setupORB(int threshold, Mat img, MatOfKeyPoint keyPoint, Mat descriptor )
    {
        ORB orb = ORB.create(threshold);
        orb.detectAndCompute(img,new Mat(), keyPoint, descriptor);

        return orb;
    }

}
