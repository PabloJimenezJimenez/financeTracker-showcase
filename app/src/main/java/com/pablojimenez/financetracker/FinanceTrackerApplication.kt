package com.pablojimenez.financetracker

import android.app.Application
import com.pablojimenez.financetracker.data.FinanceDatabase
import com.pablojimenez.financetracker.data.RoomFinanceRepository
import com.pablojimenez.financetracker.domain.AppLock
import com.pablojimenez.financetracker.domain.FinanceRepository

/** Holds what lives as long as the process: the App lock and the one database, opened on first use. */
class FinanceTrackerApplication : Application() {
    val appLock = AppLock()

    val repository: FinanceRepository by lazy { RoomFinanceRepository(FinanceDatabase.create(this)) }
}
