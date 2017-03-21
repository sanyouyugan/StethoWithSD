package com.dfire.retail.cashdesk;

import android.app.Application;
import android.content.Context;
import android.os.Environment;

import com.facebook.stetho.Stetho;
import com.facebook.stetho.dumpapp.DumperPlugin;
import com.facebook.stetho.inspector.database.DatabaseFilesProvider;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.module.Database;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建时间: 17/3/18
 * 类描述:
 *
 * @author 秋刀鱼
 * @version 1.0
 */

public class DebugApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Context context = getApplicationContext();
        /*
        ＊Stetho 通过 databaseFilesProvider 提供数据库名称
        * 默认使用SqlDriber执行数据库操作，sql  通过 openDatabase 打开数据库
        *  File databaseFile = mContext.getDatabasePath(databaseName);
        *  继承Driver，重写 openDatabase() ，见MDriver
        *  修改 DefaultInspectorModulesBuilder.finish()中的Database使用的Driver
        * */

        final DatabaseFilesProvider databaseFilesProvider = new DatabaseFilesProvider() {
            @Override
            public List<File> getDatabaseFiles() {
                List<File> databaseFiles = new ArrayList();
                for (String filename : context.databaseList()) {
                    databaseFiles.add(new File(filename));
                }
                //添加自己的数据库地址
                databaseFiles.add(new File(Environment.getExternalStorageDirectory() + "/" + "retail.db"));
                return databaseFiles;
            }

        };
        Stetho.DefaultInspectorModulesBuilder builder = new Stetho.DefaultInspectorModulesBuilder(context).databaseFiles(databaseFilesProvider);
        //替换DataBase
        ArrayList<ChromeDevtoolsDomain> list = (ArrayList) builder.finish();

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof Database) {
                Database database = new Database();
                database.add(new MDriver(this, databaseFilesProvider));
                list.set(i, database);
            }
        }


        final Iterable<ChromeDevtoolsDomain> fixded = list;

        Stetho.initialize(new Stetho.Initializer(context) {
            @Override
            protected Iterable<DumperPlugin> getDumperPlugins() {
                return new Stetho.DefaultDumperPluginsBuilder(context).finish();
            }

            @Override
            protected Iterable<ChromeDevtoolsDomain> getInspectorModules() {
                return fixded;
            }
        });
    }
}


