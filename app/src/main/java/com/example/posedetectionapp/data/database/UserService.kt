package com.example.posedetectionapp.data.database

import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.example.posedetectionapp.data.models.User
import com.example.posedetectionapp.data.database.UserDbHelper.UserContract.UserEntry

class UserService(context: Context) {

    private val dbHelper: UserDbHelper = UserDbHelper(context)

    fun createUser(user: User): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(UserEntry.COLUMN_NAME_NAME, user.name)
            put(UserEntry.COLUMN_NAME_EMAIL, user.email)
            put(UserEntry.COLUMN_NAME_PASSWORD, user.password)
            put(UserEntry.COLUMN_NAME_AGE, user.age)
        }

        val newRowId = db?.insert(UserEntry.TABLE_NAME, null, values)
        if (newRowId != null && newRowId > 0) return true
        return false
    }

    fun loginUser(user: User): Boolean {
        val db = dbHelper.readableDatabase
        val projection = arrayOf(BaseColumns._ID, UserEntry.COLUMN_NAME_NAME, UserEntry.COLUMN_NAME_EMAIL,
                UserEntry.COLUMN_NAME_AGE)
        val selection = "${UserEntry.COLUMN_NAME_EMAIL} = ? AND ${UserEntry.COLUMN_NAME_PASSWORD} = ?"
        val selectionArgs = arrayOf(user.email, user.password)
        val cursor = db.query(
                UserEntry.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        )

        return if (!(cursor.moveToFirst()) || cursor.count == 0) {
            cursor.close()
            false
        } else {
            cursor.close()
            true
        }
    }
}