@Dao
abstract class AppRuntimeDao {
    @Query("INSERT INTO sessions(app_time) VALUES (:startTime)")
    abstract suspend fun newSession(startTime: Long = app.startTime): Long

    @Query("SELECT * FROM sessions ORDER BY id DESC LIMIT 1")
    abstract suspend fun getSession(): AppRuntimeSessionEntity

    @Query("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions ORDER BY id DESC LIMIT :n)")
    abstract suspend fun truncateSessions(n: Int = 3)

    @Query("DELETE FROM sessions")
    abstract suspend fun dropSessions()
}
