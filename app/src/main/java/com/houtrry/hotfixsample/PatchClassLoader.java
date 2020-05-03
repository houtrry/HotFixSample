package com.houtrry.hotfixsample;

import java.io.File;

import dalvik.system.BaseDexClassLoader;

/**
 * @author: houtrry
 * @time: 2020/5/3
 * @desc:
 */
public class PatchClassLoader extends BaseDexClassLoader {
    public PatchClassLoader(String dexPath, File optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, optimizedDirectory, librarySearchPath, parent);
    }
}
