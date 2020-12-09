package com.example.posedetectionapp.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

class UserDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    object UserContract {
        object UserEntry : BaseColumns {
            const val TABLE_NAME = "users"
            const val COLUMN_NAME_NAME = "name"
            const val COLUMN_NAME_EMAIL = "email"
            const val COLUMN_NAME_PASSWORD = "password"
            const val COLUMN_NAME_AGE = "age"
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }


    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "FeedReader.db"
        private const val SQL_CREATE_ENTRIES =
                "CREATE TABLE ${UserContract.UserEntry.TABLE_NAME} (" +
                        "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                        "${UserContract.UserEntry.COLUMN_NAME_NAME} TEXT," +
                        "${UserContract.UserEntry.COLUMN_NAME_EMAIL} TEXT," +
                        "${UserContract.UserEntry.COLUMN_NAME_PASSWORD} TEXT," +
                        "${UserContract.UserEntry.COLUMN_NAME_AGE} INTEGER)"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${UserContract.UserEntry.TABLE_NAME}"
    }
}