
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    
    @Test
    fun testMainActivityLaunch() {
        ActivityScenario.launch(MainActivity::class.java)
        
        // Verify initial UI elements are displayed
        onView(withId(R.id.sourceLanguageSpinner))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.targetLanguageSpinner))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.b
            .check(matches(isDisplayed()))
    }

    @Test
    fun testLanguageSpinnerSelection() {
        ActivityScenario.launch(MainActivity::class.java)
        
        // Test source language spinner
        onView(withId(R.id.sourceLanguageSpinner))
            .check(matches(withSpinnerText("Japanese")))
        
        // Test target language spinner
        onView(withId(R.id.targetLanguageSpinner))
            .perform(click())
        // Select English from dropdown
        onView(withText("English")).perform(click())
        onView(withId(R.id.targetLanguageSpinner))
            .check(matches(withSpinnerText("English")))
    }

    @Test
    fun testModelSelectionRadioButtons() {
        ActivityScenario.launch(MainActivity::class.java)
        
        // Verify OCR model selection
        onView(withId(R.id.radioMlKit))
            .check(matches(isChecked()))
        
        onView(withId(R.id.radioTesseract))
            .perform(click())
            .check(matches(isChecked()))
    }
}
