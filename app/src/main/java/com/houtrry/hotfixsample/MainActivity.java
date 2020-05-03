package com.houtrry.hotfixsample;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.houtrry.hotfixsample.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityMainBinding binding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(binding.getRoot());
        binding.tvDivide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.tvResult.setText(Utils.helloWorld());
            }
        });
        binding.tvFix.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadHotFixDexFile();
            }
        });

        printClassLoaderInfo();
        printDexPathListInfo();
    }

    private void printClassLoaderInfo() {
        ClassLoader classLoader = getClassLoader();
        log("classLoader: "+classLoader);
        ClassLoader parent = classLoader.getParent();
        log("parent: "+parent);
        ClassLoader grandfather = parent.getParent();
        log("grandfather: "+grandfather);
    }

    private void printDexPathListInfo() {
        ClassLoader classLoader = getClassLoader();
        try {
            Class<?> clsBaseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = clsBaseDexClassLoader.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(classLoader);
            log("pathList: "+pathList);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    private Handler mMainHandle = new Handler(Looper.getMainLooper());
    private void downloadHotFixDexFile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String dexDir = HotFixManager.getDexDir(getApplication());
                File dirFile = new File(dexDir);
                if (!dirFile.exists()) {
                    dirFile.mkdir();
                }
                File destFile = new File(dexDir, "hotFixCopy.dex");
                try (InputStream inputStream = getAssets().open("dex/hotFix.dex");
                     OutputStream outputStream = new FileOutputStream(destFile);){

                    byte[] buffer  = new byte[1024];
                    int length = 0;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.flush();
                    mMainHandle.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载成功", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    mMainHandle.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();

    }
}
