package com.zmsoft.kds.library.core.framework.monitor.debugger;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;

import com.facebook.stetho.Stetho;
import com.facebook.stetho.common.Util;
import com.facebook.stetho.dumpapp.DumperPlugin;
import com.facebook.stetho.inspector.database.DatabaseConnectionProvider;
import com.facebook.stetho.inspector.database.DatabaseFilesProvider;
import com.facebook.stetho.inspector.database.DefaultDatabaseConnectionProvider;
import com.facebook.stetho.inspector.database.DefaultDatabaseFilesProvider;
import com.facebook.stetho.inspector.database.SQLiteDatabaseCompat;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.module.Database;
import com.facebook.stetho.inspector.protocol.module.DatabaseConstants;
import com.facebook.stetho.inspector.protocol.module.DatabaseDescriptor;
import com.facebook.stetho.inspector.protocol.module.DatabaseDriver2;
import com.zmsoft.kds.library.core.bussiness.instanceprovider.db.helper.DBManager;
import com.zmsoft.kds.library.core.bussiness.lanmode.db.DBMasterManager;
import com.zmsoft.kds.library.core.framework.monitor.debugger.dump.IndexDumperPlugin;
import com.zmsoft.kds.library.core.framework.monitor.debugger.dump.InfoDumperPlugin;
import com.zmsoft.kds.library.core.framework.monitor.debugger.dump.MasterDumperPlugin;
import com.zmsoft.kds.library.core.framework.monitor.debugger.dump.PrinterDumperPlugin;
import com.zmsoft.kds.library.core.framework.monitor.debugger.dump.PushDumperPlugin;
import com.zmsoft.kds.library.core.framework.monitor.debugger.dump.TrackerDumperPlugin;
import com.zmsoft.kds.library.core.framework.plugin.ServiceManager;
import com.zmsoft.kds.library.sdk.android.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 创建时间: 2017/9/8
 * 类描述:
 * stetho增加SD卡数据库支持
 * 与greenDao使用同一个SqliteDatabase,防止调试时候DBLocked问题
 * 修改stetho数据库操作后直接关闭数据库导致greenDao数据操作问题
 * 适用于stetho-1.5.0版本
 *
 * @author 秋刀鱼
 * @version 1.0
 */

public class StethoWrapper {

    public static void initial() {

        final Context context = (Application) ServiceManager.platform.getContext();
        //把sd卡目录数据路径添加进来
        final DatabaseFilesProvider databaseFilesProvider = new DatabaseFilesProvider() {
            @Override
            public List<File> getDatabaseFiles() {
                List<File> databaseFiles = new ArrayList();
                //只支持自己的数据，添加自己的业务数据库地址，其他数据库存在db locked问题
                databaseFiles.add(DBManager.getDBFile());
                databaseFiles.add(DBMasterManager.getDBFile());
                return databaseFiles;
            }

        };

        MySqliteDatabaseDriver db = new MySqliteDatabaseDriver(
                context,
                databaseFilesProvider,
                new MyConnectProvider());

        final Stetho.DefaultInspectorModulesBuilder builder =
                new Stetho.DefaultInspectorModulesBuilder(context)
                        .provideDatabaseDriver(db);

        Stetho.initialize(new Stetho.Initializer(context) {
            @Override
            protected Iterable<DumperPlugin> getDumperPlugins() {
                return new Stetho.DefaultDumperPluginsBuilder(context)
                        .provide(new TrackerDumperPlugin())
                        .provide(new InfoDumperPlugin())
                        .provide(new PrinterDumperPlugin())
                        .provide(new IndexDumperPlugin())
                        .provide(new PushDumperPlugin())
                        .provide(new MasterDumperPlugin())
                        .finish();
            }

            @Override
            protected Iterable<ChromeDevtoolsDomain> getInspectorModules() {
                return builder.finish();
            }
        });

    }

    /**
     * 使用greenDao的链接，防止多次打开DB
     */
    static class MyConnectProvider extends DefaultDatabaseConnectionProvider {
        public MyConnectProvider() {
        }

        @Override
        protected SQLiteDatabase performOpen(File databaseFile, @SQLiteDatabaseCompat.SQLiteOpenOptions int options) {
            SQLiteDatabaseCompat compatInstance = SQLiteDatabaseCompat.getInstance();
            int flags = SQLiteDatabase.OPEN_READWRITE;
            flags |= compatInstance.provideOpenFlags(options);
            SQLiteDatabase db = DBManager.getDataBase();
            if (databaseFile.getName().equals(StorageUtils.DEFAULT_DB_NAME)) {
                db = DBManager.getDataBase();
            } else if (databaseFile.getName().equals(StorageUtils.DEFAULT_MASTER_DB_NAME)) {
                db = DBMasterManager.getDataBase();
            }
            compatInstance.enableFeatures(options, db);
            return db;
        }
    }

    /**
     * 替换原来的数据库操作后直接关闭数据库导致reopen clnosed db问题
     * 数据库操作后不关闭数据库
     */
    static class MySqliteDatabaseDriver extends DatabaseDriver2<MySqliteDatabaseDriver.SqliteDatabaseDescriptor> {
        private static final String[] UNINTERESTING_FILENAME_SUFFIXES = new String[]{
                "-journal",
                "-shm",
                "-uid",
                "-wal"
        };

        private final DatabaseFilesProvider mDatabaseFilesProvider;
        private final DatabaseConnectionProvider mDatabaseConnectionProvider;

        /**
         * Constructs the object with a {@link DatabaseFilesProvider} that supplies the database files
         * from {@link Context#databaseList()}.
         *
         * @param context the context
         */
        @Deprecated
        public MySqliteDatabaseDriver(Context context) {
            this(
                    context,
                    new DefaultDatabaseFilesProvider(context),
                    new DefaultDatabaseConnectionProvider());
        }

        /**
         */
        @Deprecated
        public MySqliteDatabaseDriver(
                Context context,
                DatabaseFilesProvider databaseFilesProvider) {
            this(
                    context,
                    databaseFilesProvider,
                    new DefaultDatabaseConnectionProvider());
        }

        /**
         * @param context                    the context
         * @param databaseFilesProvider      a database file name provider
         * @param databaseConnectionProvider a database connection provider
         */
        public MySqliteDatabaseDriver(
                Context context,
                DatabaseFilesProvider databaseFilesProvider,
                DatabaseConnectionProvider databaseConnectionProvider) {
            super(context);
            mDatabaseFilesProvider = databaseFilesProvider;
            mDatabaseConnectionProvider = databaseConnectionProvider;
        }

        /**
         * Attempt to smartly eliminate uninteresting shadow databases such as -journal and -uid.  Note
         * that this only removes the database if it is true that it shadows another database lacking
         * the uninteresting suffix.
         *
         * @param databaseFiles Raw list of database files.
         * @return Tidied list with shadow databases removed.
         */
        // @VisibleForTesting
        static List<File> tidyDatabaseList(List<File> databaseFiles) {
            Set<File> originalAsSet = new HashSet<File>(databaseFiles);
            List<File> tidiedList = new ArrayList<File>();
            for (File databaseFile : databaseFiles) {
                String databaseFilename = databaseFile.getPath();
                String sansSuffix = removeSuffix(databaseFilename, UNINTERESTING_FILENAME_SUFFIXES);
                if (sansSuffix.equals(databaseFilename) || !originalAsSet.contains(new File(sansSuffix))) {
                    tidiedList.add(databaseFile);
                }
            }
            return tidiedList;
        }

        private static String removeSuffix(String str, String[] suffixesToRemove) {
            for (String suffix : suffixesToRemove) {
                if (str.endsWith(suffix)) {
                    return str.substring(0, str.length() - suffix.length());
                }
            }
            return str;
        }

        private static String getFirstWord(String s) {
            s = s.trim();
            int firstSpace = s.indexOf(' ');
            return firstSpace >= 0 ? s.substring(0, firstSpace) : s;
        }

        @Override
        public List<MySqliteDatabaseDriver.SqliteDatabaseDescriptor> getDatabaseNames() {
            ArrayList<MySqliteDatabaseDriver.SqliteDatabaseDescriptor> databases = new ArrayList<>();
            List<File> potentialDatabaseFiles = mDatabaseFilesProvider.getDatabaseFiles();
            Collections.sort(potentialDatabaseFiles);
            Iterable<File> tidiedList = tidyDatabaseList(potentialDatabaseFiles);
            for (File database : tidiedList) {
                databases.add(new MySqliteDatabaseDriver.SqliteDatabaseDescriptor(database));
            }
            return databases;
        }

        public List<String> getTableNames(MySqliteDatabaseDriver.SqliteDatabaseDescriptor databaseDesc)
                throws SQLiteException {
            SQLiteDatabase database = openDatabase(databaseDesc);
            try {
                Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type IN (?, ?)",
                        new String[]{"table", "view"});
                try {
                    List<String> tableNames = new ArrayList<String>();
                    while (cursor.moveToNext()) {
                        tableNames.add(cursor.getString(0));
                    }
                    return tableNames;
                } finally {
                    cursor.close();
                }
            } finally {
                //database.close();
            }
        }

        public Database.ExecuteSQLResponse executeSQL(
                MySqliteDatabaseDriver.SqliteDatabaseDescriptor databaseDesc,
                String query,
                ExecuteResultHandler<Database.ExecuteSQLResponse> handler)
                throws SQLiteException {
            Util.throwIfNull(query);
            Util.throwIfNull(handler);
            SQLiteDatabase database = openDatabase(databaseDesc);
            try {
                String firstWordUpperCase = getFirstWord(query).toUpperCase();
                switch (firstWordUpperCase) {
                    case "UPDATE":
                    case "DELETE":
                        return executeUpdateDelete(database, query, handler);
                    case "INSERT":
                        return executeInsert(database, query, handler);
                    case "SELECT":
                    case "PRAGMA":
                    case "EXPLAIN":
                        return executeSelect(database, query, handler);
                    default:
                        return executeRawQuery(database, query, handler);
                }
            } finally {
                //database.close();
            }
        }

        @TargetApi(DatabaseConstants.MIN_API_LEVEL)
        private <T> T executeUpdateDelete(
                SQLiteDatabase database,
                String query,
                ExecuteResultHandler<T> handler) {
            SQLiteStatement statement = database.compileStatement(query);
            int count = statement.executeUpdateDelete();
            return handler.handleUpdateDelete(count);
        }

        private <T> T executeInsert(
                SQLiteDatabase database,
                String query,
                ExecuteResultHandler<T> handler) {
            SQLiteStatement statement = database.compileStatement(query);
            long count = statement.executeInsert();
            return handler.handleInsert(count);
        }

        private <T> T executeSelect(
                SQLiteDatabase database,
                String query,
                ExecuteResultHandler<T> handler) {
            Cursor cursor = database.rawQuery(query, null);
            try {
                return handler.handleSelect(cursor);
            } finally {
                cursor.close();
            }
        }

        private <T> T executeRawQuery(
                SQLiteDatabase database,
                String query,
                ExecuteResultHandler<T> handler) {
            database.execSQL(query);
            return handler.handleRawQuery();
        }

        private SQLiteDatabase openDatabase(
                MySqliteDatabaseDriver.SqliteDatabaseDescriptor databaseDesc)
                throws SQLiteException {
            Util.throwIfNull(databaseDesc);
            return mDatabaseConnectionProvider.openDatabase(databaseDesc.file);
        }

        static class SqliteDatabaseDescriptor implements DatabaseDescriptor {
            public final File file;

            public SqliteDatabaseDescriptor(File file) {
                this.file = file;
            }

            @Override
            public String name() {
                return file.getName();
            }
        }

    }

}
