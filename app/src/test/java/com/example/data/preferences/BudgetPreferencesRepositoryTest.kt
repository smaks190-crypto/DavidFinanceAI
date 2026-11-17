package com.example.data.preferences

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BudgetPreferencesRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(BudgetPreferencesRepository.BUDGET_PREFS_NAME, 0)
            .edit().clear().commit()
    }

    @After
    fun clearPreferencesAfterTest() = clearPreferences()

    @Test
    fun newProfileDoesNotInheritAnotherProfilesSafetySettings() {
        BudgetPreferencesRepository.setUserSafetyFloorRub(context, 10_000.0, "profile-a")
        BudgetPreferencesRepository.setUserEssentialDailyNormRub(context, 50.0, "profile-a")

        assertEquals(10_000.0, BudgetPreferencesRepository.getUserSafetyFloorRub(context, "profile-a"), 0.01)
        assertEquals(50.0, BudgetPreferencesRepository.getUserEssentialDailyNormRub(context, "profile-a")!!, 0.01)
        assertEquals(0.0, BudgetPreferencesRepository.getUserSafetyFloorRub(context, "profile-b"), 0.01)
        assertNull(BudgetPreferencesRepository.getUserEssentialDailyNormRub(context, "profile-b"))
    }

    @Test
    fun legacyGlobalSettingsRemainAvailableToTheDefaultProfileOnly() {
        context.getSharedPreferences(BudgetPreferencesRepository.BUDGET_PREFS_NAME, 0).edit()
            .putFloat("user_safety_floor_rub", 2_500f)
            .putFloat("user_essential_daily_norm_rub", 50f)
            .commit()

        assertEquals(2_500.0, BudgetPreferencesRepository.getUserSafetyFloorRub(context, "default"), 0.01)
        assertEquals(50.0, BudgetPreferencesRepository.getUserEssentialDailyNormRub(context, "default")!!, 0.01)
        assertEquals(0.0, BudgetPreferencesRepository.getUserSafetyFloorRub(context, "new-profile"), 0.01)
        assertNull(BudgetPreferencesRepository.getUserEssentialDailyNormRub(context, "new-profile"))
    }
}
