package com.heinrichreimer.inquiry;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.heinrichreimer.inquiry.convert.Converter;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class Inquiry {

    public static final String ID = "_id";

    static final String DEBUG_TAG = "Inquiry";

    private static Inquiry inquiry;

    Context context;
    Handler handler;
    @Nullable String databaseName;
    int databaseVersion = 1;
    private final List<Converter> converters = new LinkedList<>();

    public Inquiry(@NonNull Context context,
                   @Nullable String databaseName,
                   @IntRange(from = 1, to = Integer.MAX_VALUE) int databaseVersion) {

        Preconditions.checkNotNull(context);

        try {
            // try to create the handler inside the calling thread. If this thread does not have a
            // default looper, e.g. JobService(s)...
            handler = new Handler();
        } catch (RuntimeException e) {
            // ... exception will be thrown, so get the main looper
            Log.w(Inquiry.DEBUG_TAG, "Caller thread has no default looper, using main looper instead");
            handler = new Handler(Looper.getMainLooper());
        }

        this.context = context;
        this.databaseName = databaseName;
        this.databaseVersion = databaseVersion;
    }

    @NonNull
    public static Inquiry init(@NonNull Context context, @Nullable String databaseName,
                               @IntRange(from = 1, to = Integer.MAX_VALUE) int databaseVersion) {
        Preconditions.checkNotNull(context);
        if (inquiry != null) {
            throw new IllegalStateException("Inquiry already initialized, call Inquiry.deinit() first");
        }

        inquiry = new Inquiry(context, databaseName, databaseVersion);
        return inquiry;
    }

    public void destroy() {
        context = null;
        handler = null;
        databaseName = null;
        databaseVersion = 0;
    }

    public boolean isAlive() {
        return context != null && handler != null && databaseName != null && databaseVersion > 0;
    }

    public static void deinit() {
        if (inquiry != null) {
            inquiry.destroy();
            inquiry = null;
        }
    }

    @NonNull
    public static Inquiry get() {
        if (inquiry == null)
            throw new IllegalStateException("Inquiry not initialized, or has been garbage collected.");
        return inquiry;
    }

    public void addConverter(Converter converter) {
        converters.add(converter);
    }

    List<Converter> getConverters() {
        List<Converter> converters = new LinkedList<>();
        converters.addAll(ConverterFactory.getHighPriorityConverters());
        converters.addAll(this.converters);
        converters.addAll(ConverterFactory.getLowPriorityConverters());
        return Collections.unmodifiableList(converters);
    }

    public void dropTable(@NonNull Class<?> type) {
        Preconditions.checkNotNull(type);
        if (!DatabaseSchemaParser.isTable(type))
            throw new UnsupportedOperationException("Unable to drop table for type " + type.getSimpleName());

        String table = DatabaseSchemaParser.getTableName(type);
        DatabaseHelper database = new DatabaseHelper(context, databaseName, table, null, databaseVersion, /* onUpgrade */ null);
        database.dropTable();
        database.close();
    }

    @NonNull
    public <RowType> Query<RowType, Long[]> select(@NonNull Class<RowType> rowType) {
        Preconditions.checkNotNull(rowType);
        return new Query<>(this, Query.SELECT, rowType);
    }

    @NonNull
    public <RowType> Query<RowType, Long[]> insert(@NonNull Class<RowType> rowType) {
        Preconditions.checkNotNull(rowType);
        return new Query<>(this, Query.INSERT, rowType);
    }

    @NonNull
    public <RowType> Query<RowType, Long[]> insertOrIgnore(@NonNull Class<RowType> rowType) {
        Preconditions.checkNotNull(rowType);
        return new Query<>(this, Query.INSERT_OR_IGNORE, rowType);
    }

    @NonNull
    public <RowType> Query<RowType, Long[]> replace(@NonNull Class<RowType> rowType) {
        Preconditions.checkNotNull(rowType);
        return new Query<>(this, Query.REPLACE, rowType);
    }

    @NonNull
    public <RowType> Query<RowType, Integer> update(@NonNull Class<RowType> rowType) {
        Preconditions.checkNotNull(rowType);
        return new Query<>(this, Query.UPDATE, rowType);
    }

    @NonNull
    public <RowType> Query<RowType, Integer> delete(@NonNull Class<RowType> rowType) {
        Preconditions.checkNotNull(rowType);
        return new Query<>(this, Query.DELETE, rowType);
    }
}