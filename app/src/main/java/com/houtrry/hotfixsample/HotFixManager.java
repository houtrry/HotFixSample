package com.houtrry.hotfixsample;

import android.app.Application;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import dalvik.system.PathClassLoader;

/**
 * @author: houtrry
 * @time: 2020/5/2
 * @desc:
 */
public class HotFixManager {

    private static final String DEX_DIR = "dex";

    /**
     * 在ClassLoader中的dexElements数组中（数组0号位）插入我们自己的dex
     *
     * @param application
     */
    public static void preformHotFix(@NonNull Application application) {
        if (!hasDex(application)) {
            return;
        }
        try {
            //第一步：获取当前ClassLoader中的dexElements(dexElementsOld)
            ClassLoader classLoader = application.getClassLoader();
            Class<?> clsBaseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = clsBaseDexClassLoader.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(classLoader);
            Class<?> clsDexPathList = Class.forName("dalvik.system.DexPathList");
            Field dexElementsField = clsDexPathList.getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] dexElementsOld = (Object[]) dexElementsField.get(pathList);
            System.out.println("dexElementsOld: " + dexElementsOld);

            int sizeOfOldDexElement = dexElementsOld.length;
            List<File> dexFileList = getDexFileList(getDexDir(application));

            Method[] declaredMethods = clsDexPathList.getDeclaredMethods();
            for (Method method :
                    declaredMethods) {
                System.out.println("method: " + method);
            }

            //第二步：生成包含hot fix dex文件的dexElements(dexElementsNew)
            //当然，也可以用另外一种方式： new 一个PathClassLoader加载dex文件夹下的dex，
            //                          然后反射获取到这个PathClassLoader中dexElements的值，
            //                          也就是我们这里需要的，反射逻辑可以参考第一步
            //PathClassLoader pathClassLoader = new PathClassLoader(getDexPath(getDexDir(application)), classLoaderParent);
            //注意：makeDexElements在不同版本中可能会有变化，注意log提示，做好兼容, 这里只是测试.
            //      具体的兼容逻辑可以参考腾讯tinker的com.tencent.tinker.loader.SystemClassLoaderAdder#installDexes
            Method makeDexElementsMethod = clsDexPathList.getDeclaredMethod("makeDexElements",
                    List.class, File.class, List.class, ClassLoader.class);
            makeDexElementsMethod.setAccessible(true);
            Object[] dexElementsNew = (Object[]) makeDexElementsMethod.invoke(null, dexFileList, null,
                    new ArrayList<IOException>(), classLoader);
            int sizeOfNewDexElement = dexElementsNew.length;
            System.out.println("sizeOfNewDexElement: " + sizeOfNewDexElement + ", sizeOfOldDexElement: " + sizeOfOldDexElement);
            if (sizeOfNewDexElement == 0) {
                return;
            }
            //第三步：合并两个dexElements
            //注意：dexElementsNew中的元素需要放到dexElementsOld元素的前面
            //数组拷贝逻辑可以参考DexPathList#addDexPath方法
//            Object[] dexElements = new Object[sizeOfNewDexElement + sizeOfOldDexElement];
            //注意：这里不要像直接像上面那样直接new Object[]数组，而是使用Array.newInstance方法（参考自tinker的com.tencent.tinker.loader.shareutil.ShareReflectUtil#expandFieldArray）
            //直接new Object[]数组的话，在执行下面dexElementsField.set的时候会报错java.lang.RuntimeException: Unable to instantiate application com.houtrry.hotfixsample.HotFixApplication: java.lang.IllegalArgumentException: field dalvik.system.DexPathList.dexElements has type dalvik.system.DexPathList$Element[], got java.lang.Object[]
            Object[] dexElements = (Object[]) Array.newInstance(dexElementsOld.getClass().getComponentType(), sizeOfNewDexElement + sizeOfOldDexElement);

            System.arraycopy(dexElementsNew, 0, dexElements, 0, sizeOfNewDexElement);
            System.arraycopy(dexElementsOld, 0, dexElements, sizeOfNewDexElement, sizeOfOldDexElement);
            System.out.println("dexElements: " + dexElements);
            //第四步：替换dexElements
            dexElementsField.setAccessible(true);
            dexElementsField.set(pathList, dexElements);
            System.out.println("pathList: " + pathList);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 给ClassLoader安排一个新的parent
     * 在这个新parent中加载我们自己的dex
     * 简单理解就是在单链表中间插入第三个元素
     *
     * @param application
     */
    public static void preformHotFix2(@NonNull Application application) {
        if (!hasDex(application)) {
            return;
        }
        try {
            ClassLoader classLoader = application.getClassLoader();
            //第一步：反射获取到当前ClassLoader的parent
            Class<?> clsBaseDexClassLoader = Class.forName("java.lang.ClassLoader");
            Field parent = clsBaseDexClassLoader.getDeclaredField("parent");
            parent.setAccessible(true);
            //第二步：创建新的PathClassLoader
            // 这个PathClassLoader的parent是当前CLassLoader的parent
            //path指向我们dex文件夹下的dex文件
            ClassLoader classLoaderParent = classLoader.getParent();
            String dexPath = getDexPath(getDexDir(application));
            System.out.println("dexPath: "+dexPath);
            PathClassLoader pathClassLoader = new PathClassLoader(dexPath, classLoaderParent);
            //第三步：把classLoaderParent作为当前classLoader的parent
            //这样，根据双亲委托机制，当前ClassLoader加载class的时候，
            // 会将class交给它的parent（也就是我们创建的pathClassLoader来加载）
            //如果我们的pathClassLoader可以加载这个class（意味着该class能在dex中找到，也就是我们需要修复的class）
            //这样系统的ClassLoader就没有机会加载有问题的class，问题得到修复
            parent.set(classLoader, pathClassLoader);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static final String REGEX_DEX = "([a-zA-Z0-9_-]+).(dex|apk|zip)";
    private static final Pattern PATTERN = Pattern.compile(REGEX_DEX);

    private static List<File> getDexFileList(String dexDir) {
        List<File> list = new ArrayList<>();
        File file = new File(dexDir);
        if (!file.exists() || !file.isDirectory() || file.length() == 0) {
            return list;
        }
        File[] files = file.listFiles();
        if (files == null) {
            return list;
        }

        for (File fileItem : files) {
            if (PATTERN.matcher(fileItem.getName()).matches()) {
                list.add(fileItem);
            }
        }
        return list;
    }

    private static String getDexPath(String dexDir) {
        final StringBuilder sb = new StringBuilder();
        final File file = new File(dexDir);
        if (file.exists() && file.isDirectory() && file.length() > 0) {
            File[] files = file.listFiles();
            if (files != null) {
                File fileItem;
                final int size = files.length;
                for (int i = 0; i < size; i++) {
                    fileItem = files[i];
                    if (PATTERN.matcher(fileItem.getName()).matches()) {
                        sb.append(fileItem.getAbsolutePath());
                        sb.append(File.pathSeparator);
                    }
                }
            }
        }
        String dexPath = sb.toString();
        if (dexPath.endsWith(File.pathSeparator)) {
            dexPath = dexPath.substring(0, dexPath.length()-1);
        }
        return dexPath;
    }

    public static String getDexDir(@NonNull Application application) {
        return application.getCacheDir() + File.separator + DEX_DIR;
    }

    private static boolean hasDex(@NonNull Application application) {
        final String dexDir = getDexDir(application);
        final File file = new File(dexDir);
        return file.exists() && file.length() > 0;
    }

}
