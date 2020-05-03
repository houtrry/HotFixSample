package com.houtrry.hotfixsample;

import android.content.Context;

import androidx.multidex.MultiDexApplication;

/**
 * @author: houtrry
 * @time: 2020/5/1
 * @desc:
 */
public class HotFixApplication extends MultiDexApplication {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        HotFixManager.preformHotFix(this);
    }

}
