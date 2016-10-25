package com.heinrichreimer.inquiry;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.heinrichreimer.inquiry.callbacks.RunCallback;
import com.heinrichreimer.inquiry.callbacks.UpgradeCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public final class Query<RowType, RunReturn> implements UpgradeCallback {

    @IntDef({SELECT, INSERT, REPLACE, UPDATE, DELETE, INSERT_OR_IGNORE})
    @Retention(RetentionPolicy.SOURCE)
    @interface QueryType {
    }

    final static int SELECT = 1;
    final static int INSERT = 2;
    final static int REPLACE = 3;
    final static int UPDATE = 4;
    final static int DELETE = 5;
    final static int INSERT_OR_IGNORE = 6;

    @NonNull
    private final Inquiry inquiry;
    @QueryType
    private final int queryType;
    @NonNull
    private final Class<RowType> rowType;
    @NonNull
    private DatabaseHelper database;

    private String[] onlyUpdate;
    private final List<String> selection = new LinkedList<>();
    private final List<Object> selectionArgs = new LinkedList<>();
    private final List<String> sortOrder = new LinkedList<>();
    private int limit;
    private int offset;
    private RowType[] values;

    Query(@NonNull Inquiry inquiry, @QueryType int type, @NonNull Class<RowType> rowType) {
        this.inquiry = inquiry;
        queryType = type;
        this.rowType = rowType;
        if (inquiry.databaseName == null)
            throw new IllegalStateException("Inquiry was not initialized with a database name, it can only use content providers in this configuration.");
        database = new DatabaseHelper(inquiry.context, inquiry.databaseName,
                DatabaseSchemaParser.getTableName(rowType),
                DatabaseSchemaParser.getClassSchema(inquiry.getConverters(), rowType)
                , inquiry.databaseVersion, /* onUpgrade */ this);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        List<String> queryList = DatabaseSchemaParser.alterDatabase(inquiry.getConverters(), rowType, oldVersion, newVersion);
        if (queryList == null) {
            // onUpgrade without new fields?...re-create to be safe.
            Log.e(Inquiry.DEBUG_TAG, "onUpgrade: No new COLUMN to be added...O_o...re-creating db");
            database.dropTable();
            database.onCreate(db);
        } else {
            for (String query : queryList) {
                if(BuildConfig.DEBUG)
                    Log.d(Inquiry.DEBUG_TAG, "onUpgrade: " + query);
                db.execSQL(query);
            }
        }
    }

    public Query<RowType, RunReturn> atPosition(@IntRange(from = 0, to = Integer.MAX_VALUE) int position) {
        Cursor cursor = database.query(null, getSelection(), getSelectionArgs(), null);
        if (cursor != null) {
            if (position < 0 || position >= cursor.getCount()) {
                cursor.close();
                throw new IndexOutOfBoundsException(String.format(Locale.US,
                        "Position %d is out of bounds for cursor of size %d.",
                        position, cursor.getCount()));
            }
            if (!cursor.moveToPosition(position)) {
                cursor.close();
                throw new IllegalStateException(String.format(Locale.US,
                        "Unable to move to position %d in cursor of size %d.",
                        position, cursor.getCount()));
            }
            int idIndex = cursor.getColumnIndex(Inquiry.ID);
            if (idIndex < 0) {
                cursor.close();
                throw new IllegalStateException("Didn't find a column named " +
                        Inquiry.ID + " in this Cursor.");
            }
            long idValue = cursor.getLong(idIndex);
            selection.clear();
            selectionArgs.clear();
            where(Inquiry.ID + " = ?", idValue);
            cursor.close();
        }
        return this;
    }

    public Query<RowType, RunReturn> where(@NonNull String selection, @Nullable Object... selectionArgs) {
        Preconditions.checkNotNull(selection);

        int args = Utils.countOccurrences(selection, '?');
        if ((selectionArgs == null && args != 0) ||
                (selectionArgs != null && selectionArgs.length != args))
            throw new IllegalArgumentException("There must be exactly as many selection args as " +
                    "'?' characters in the selection string.");
        this.selection.add(selection);
        if (selectionArgs != null) {
            Collections.addAll(this.selectionArgs, selectionArgs);
        }
        return this;
    }

    public Query<RowType, RunReturn> whereIn(@NonNull String column, @Nullable Object... selectionArgs) {
        Preconditions.checkNotNull(column);

        if (selectionArgs == null)
            return this;

        StringBuilder in = new StringBuilder();
        in.append(column);
        in.append(" IN (");
        boolean first = true;
        for (Object ignored : selectionArgs) {
            if (first)
                first = false;
            else
                in.append(",");
            in.append("?");
        }
        in.append(")");
        this.selection.add(in.toString());
        Collections.addAll(this.selectionArgs, selectionArgs);
        return this;
    }

    public Query<RowType, RunReturn> clearWhere() {
        selection.clear();
        selectionArgs.clear();
        return this;
    }

    public Query<RowType, RunReturn> sort(@NonNull String... columns) {
        Collections.addAll(this.sortOrder, columns);
        return this;
    }

    public Query<RowType, RunReturn> clearSort() {
        sortOrder.clear();
        return this;
    }

    public Query<RowType, RunReturn> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Query<RowType, RunReturn> clearLimit() {
        this.limit = -1;
        return this;
    }

    public Query<RowType, RunReturn> offset(int offset) {
        this.offset = offset;
        return this;
    }

    public Query<RowType, RunReturn> clearOffset(int offset) {
        this.offset = offset;
        return this;
    }

    @SuppressWarnings("unchecked")
    public final Query<RowType, RunReturn> value(@NonNull Object value) {
        Preconditions.checkNotNull(value);
        values = (RowType[]) Array.newInstance(rowType, 1);
        Array.set(values, 0, value);
        return this;
    }

    @SafeVarargs
    public final Query<RowType, RunReturn> values(@NonNull RowType... values) {
        this.values = values;
        return this;
    }

    public Query<RowType, RunReturn> onlyUpdate(@NonNull String... columns) {
        onlyUpdate = columns;
        return this;
    }

    @Nullable
    public RowType one() {
        int tempLimit = limit;
        limit = 1;
        RowType[] results = getInternal();
        limit = tempLimit;
        if (results == null || results.length == 0)
            return null;
        return results[0];
    }

    public void one(@NonNull final RunCallback<RowType> callback) {
        Preconditions.checkNotNull(callback);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final RowType results = one();
                if (inquiry.handler == null) return;
                inquiry.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.result(results);
                    }
                });
            }
        }).start();
    }

    @NonNull
    public RowType[] all() {
        return getInternal();
    }

    public void all(@NonNull final RunCallback<RowType[]> callback) {
        Preconditions.checkNotNull(callback);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final RowType[] results = all();
                if (inquiry.handler == null) return;
                inquiry.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.result(results);
                    }
                });
            }
        }).start();
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public RunReturn run() {
        if (!inquiry.isAlive()) {
            switch (queryType) {
                case SELECT:
                    return (RunReturn) new Long[0];
                case INSERT_OR_IGNORE:
                    return (RunReturn) new Long[0];
                case INSERT:
                    return (RunReturn) new Long[0];
                case REPLACE:
                    return (RunReturn) new Long[0];
                case UPDATE:
                    return (RunReturn) (Integer) 0;
                case DELETE:
                    return (RunReturn) (Integer) 0;
            }
        }
        switch (queryType) {
            case SELECT:
                return (RunReturn) getIdsInternal();
            case INSERT_OR_IGNORE:
            case INSERT:
                if (values == null || values.length == 0) {
                    throw new IllegalStateException("No values were provided for this query to run.");
                }
                Long[] insertedIds = new Long[values.length];
                for (int i = 0; i < values.length; i++) {
                    ContentValues contentValues = DatabaseAdapter.save(inquiry, inquiry.getConverters(), values[i], null);
                    insertedIds[i] = (queryType == INSERT) ?
                            database.insert(contentValues) :
                            database.insertOrIgnore(contentValues);
                }
                database.close();
                return (RunReturn) insertedIds;
            case REPLACE:
                if (values == null || values.length == 0) {
                    throw new IllegalStateException("No values were provided for this query to run.");
                }
                Long[] replacedIds = new Long[values.length];
                for (int i = 0; i < values.length; i++) {
                    ContentValues contentValues = DatabaseAdapter.save(inquiry, inquiry.getConverters(), values[i], null);
                    replacedIds[i] = database.replace(contentValues);
                }
                database.close();
                return (RunReturn) replacedIds;
            case UPDATE:
                if (values == null || values.length == 0) {
                    throw new IllegalStateException("No values were provided for this query to run.");
                }
                ContentValues contentValues = DatabaseAdapter.save(inquiry, inquiry.getConverters(), values[values.length - 1], onlyUpdate);
                RunReturn updatedRows = (RunReturn) (Integer) database.update(contentValues, getSelection(), getSelectionArgs());
                database.close();
                return updatedRows;
            case DELETE: {
                RunReturn value = (RunReturn) (Integer) database.delete(getSelection(), getSelectionArgs());
                database.close();
                return value;
            }
            default:
                throw new UnsupportedOperationException();
        }
    }

    public void run(@NonNull final RunCallback<RunReturn> callback) {
        Preconditions.checkNotNull(callback);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final RunReturn changed = Query.this.run();
                if (inquiry.handler == null) return;
                inquiry.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.result(changed);
                    }
                });
            }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private RowType[] getInternal() {
        if (!inquiry.isAlive())
            return (RowType[]) Array.newInstance(rowType, 0);
        if (queryType != SELECT) {
            throw new UnsupportedOperationException("one() and all() can only be used with Inquiry.select().");
        }
        else {
            String[] projection = DatabaseSchemaParser.generateProjection(rowType);

            StringBuilder sort = new StringBuilder();
            sort.append(getSortOrder());
            if (limit > 0) sort.append(String.format(Locale.US, " LIMIT %d", limit));
            if (offset > 0) sort.append(String.format(Locale.US, " OFFSET %d", offset));

            Cursor cursor = database.query(projection, getSelection(), getSelectionArgs(), sort.toString());
            if (cursor == null)
                return (RowType[]) Array.newInstance(rowType, 0);

            RowType[] results = (RowType[]) Array.newInstance(rowType, cursor.getCount());
            if (cursor.getCount() > 0) {
                int index = 0;
                while (cursor.moveToNext()) {
                    results[index] = DatabaseAdapter.load(inquiry, inquiry.getConverters(), cursor, rowType);
                    index++;
                }
            }
            cursor.close();
            database.close();
            return results;
        }
    }

    private Long[] getIdsInternal() {
        if (!inquiry.isAlive())
            return new Long[0];
        if (queryType != SELECT) {
            throw new UnsupportedOperationException("one() and all() can only be used with Inquiry.select().");
        }
        else {
            StringBuilder sort = new StringBuilder();
            sort.append(getSortOrder());
            if (limit > 0) sort.append(String.format(Locale.US, " LIMIT %d", limit));
            if (offset > 0) sort.append(String.format(Locale.US, " OFFSET %d", offset));

            Cursor cursor = database.query(new String[]{Inquiry.ID}, getSelection(), getSelectionArgs(), sort.toString());
            if (cursor == null)
                return new Long[0];

            Long[] results = new Long[cursor.getCount()];
            if (cursor.getCount() > 0) {
                for (int i = 0; cursor.moveToNext(); i++) {
                    int idIndex = cursor.getColumnIndex(Inquiry.ID);
                    if (idIndex < 0) {
                        cursor.close();
                        throw new IllegalStateException("Didn't find a column named " + Inquiry.ID + " in this Cursor.");
                    }
                    results[i] = cursor.getLong(idIndex);
                }
            }
            cursor.close();
            database.close();
            return results;
        }
    }

    private String getSelection() {
        if (selection.isEmpty()) return null;
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        for (String sel : selection) {
            if (first) first = false;
            else sb.append(" AND ");
            sb.append(sel);
        }
        return sb.toString();
    }

    private String[] getSelectionArgs() {
        if (selectionArgs.isEmpty()) return null;
        String[] args = new String[selectionArgs.size()];
        for (int i = 0; i < selectionArgs.size(); i++) {
            args[i] = (selectionArgs.get(i) + "");
        }
        return args;
    }

    private String getSortOrder() {
        if (sortOrder.isEmpty()) return null;
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        for (String sort : sortOrder) {
            if (first) first = false;
            else sb.append(", ");
            sb.append(sort);
        }
        return sb.toString();
    }
}