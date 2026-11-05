package com.tupedalada.app

import android.app.Application
import com.tupedalada.app.data.ImportRepository
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.security.PinManager

/** Contenedor sencillo de dependencias (sin framework de inyección para mantener la beta ligera). */
class TuPedaladaApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.get(this) }
    val pin: PinManager by lazy { PinManager(this) }
    val imports: ImportRepository by lazy { ImportRepository(this, db) }
}
