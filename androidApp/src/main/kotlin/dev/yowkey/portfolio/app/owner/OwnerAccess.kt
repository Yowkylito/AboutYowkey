package dev.yowkey.portfolio.app.owner

import dev.yowkey.portfolio.admin.AdminApi
import dev.yowkey.portfolio.admin.AdminSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * What the admin view needs of the owner's session: whether a token is kept, the owner's routes with it, and signing
 * in and out. On the phone it is [OwnerSession]; the view model's tests give it a fake, so the admin view's rules run
 * on the JVM without the Keystore, WorkManager and Firebase that the session brings along.
 */
interface OwnerAccess {
    val signedIn: StateFlow<Boolean>

    /** The owner's routes, with the kept token. @throws dev.yowkey.portfolio.admin.AdminSignedOutException when there is none */
    suspend fun api(): AdminApi

    /** Checks [password] with the worker and keeps it. @see OwnerSession.signIn */
    suspend fun signIn(password: String): AdminSummary

    /** Tells the worker to stop pushing here, then forgets the owner. */
    suspend fun signOut()

    /** The worker stopped taking the token: forget it without asking the worker anything. */
    fun expire()
}

/** [OwnerSession] as the admin view sees it. */
fun OwnerSession.asAccess(): OwnerAccess {
    val session = this
    return object : OwnerAccess {
        override val signedIn: StateFlow<Boolean> get() = session.signedIn
        override suspend fun api(): AdminApi = session.api()
        override suspend fun signIn(password: String): AdminSummary = session.signIn(password)
        override suspend fun signOut() = session.signOut()
        override fun expire() = session.expire()
    }
}
