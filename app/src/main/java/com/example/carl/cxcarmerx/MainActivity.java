package com.example.carl.cxcarmerx;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private PreviewView pvCamera;
    private String rootPath;
    private String imaPath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermission();
        pvCamera = findViewById(R.id.pvCamera);
        rootPath = getSDPath(this);
        imaPath = rootPath + "/image/test.png";
        log("imgePath:"+imaPath);
        pvCamera.post(new Runnable() {
            @Override
            public void run() {
                startCamera(MainActivity.this);
            }
        });
    }

    private void startCamera(final Context context) {
        final ListenableFuture cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider processCameraProvider= (ProcessCameraProvider) cameraProviderFuture.get();
                    Preview preview = new Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .setTargetRotation(pvCamera.getDisplay().getRotation())
                            .build();

                    preview.setSurfaceProvider(pvCamera.getPreviewSurfaceProvider());
                    ImageAnalysis imageAnalysis =
                            new ImageAnalysis.Builder()
                                    .setTargetResolution(new Size(pvCamera.getWidth(), pvCamera.getHeight()))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build();

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(MainActivity.this),
                            new ImageAnalysis.Analyzer() {
                        @Override
                        public void analyze(@NonNull ImageProxy image) {
                            int rotationDegrees = image.getImageInfo().getRotationDegrees();
                            // insert your code here.
                            Bitmap bitmap = yuv2bitmap(image,rotationDegrees);
                            try {
                                bitmap.compress(Bitmap.CompressFormat.PNG,100,new FileOutputStream(new File(imaPath)));
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            image.close();
                        }
                    });
                    processCameraProvider.bindToLifecycle((LifecycleOwner) context, CameraSelector.DEFAULT_FRONT_CAMERA,
                            imageAnalysis,preview);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }, ContextCompat.getMainExecutor(context));
    }

    private Bitmap yuv2bitmap(ImageProxy image,int rotation){
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        //cameraX 获取yuv
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        //开始时间
        long START = System.currentTimeMillis();
        //获取yuvImage
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        //输出流
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //压缩写入out
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, out);
        //转数组
        byte[] imageBytes = out.toByteArray();
        //生成bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        //旋转bitmap
        Bitmap rotateBitmap = rotateBitmap(bitmap, rotation);
        return rotateBitmap;

    }
    private Bitmap rotateBitmap(Bitmap origin, float alpha) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(alpha);
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    public void requestPermission() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "申请权限", Toast.LENGTH_SHORT).show();

            // 申请 相机 麦克风权限
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,

                    Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    public String getSDPath(Context context) {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);// 判断sd卡是否存在
        if (sdCardExist) {
            if (Build.VERSION.SDK_INT>=29){
                //Android10之后
                sdDir = context.getExternalFilesDir(null);
            }else {
                sdDir = Environment.getExternalStorageDirectory();// 获取SD卡根目录
            }
        } else {
            sdDir = Environment.getRootDirectory();// 获取跟目录
        }
        return sdDir.toString();
    }
    private void log(String str){
        Log.i("global",""+str);
    }

}
