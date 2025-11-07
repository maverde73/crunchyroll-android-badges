package com.maverde.crunchybadges

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import org.json.JSONArray

/**
 * Settings Activity
 *
 * Allows users to configure:
 * - Which languages to track (max 3)
 * - Whether to filter out undubbed anime
 */
class SettingsActivity : AppCompatActivity() {

    // Language checkboxes
    private lateinit var checkItaIT: CheckBox
    private lateinit var checkEnUS: CheckBox
    private lateinit var checkEsES: CheckBox
    private lateinit var checkEs419: CheckBox
    private lateinit var checkDeDE: CheckBox
    private lateinit var checkFrFR: CheckBox
    private lateinit var checkPtBR: CheckBox
    private lateinit var checkJaJP: CheckBox
    private lateinit var checkRuRU: CheckBox

    // Filter switch
    private lateinit var switchFilterUndubbed: SwitchCompat

    // Save button
    private lateinit var btnSave: Button

    // Map checkbox to language code
    private val languageMap = mutableMapOf<CheckBox, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize views
        checkItaIT = findViewById(R.id.checkItaIT)
        checkEnUS = findViewById(R.id.checkEnUS)
        checkEsES = findViewById(R.id.checkEsES)
        checkEs419 = findViewById(R.id.checkEs419)
        checkDeDE = findViewById(R.id.checkDeDE)
        checkFrFR = findViewById(R.id.checkFrFR)
        checkPtBR = findViewById(R.id.checkPtBR)
        checkJaJP = findViewById(R.id.checkJaJP)
        checkRuRU = findViewById(R.id.checkRuRU)
        switchFilterUndubbed = findViewById(R.id.switchFilterUndubbed)
        btnSave = findViewById(R.id.btnSave)

        // Map checkboxes to language codes
        languageMap[checkItaIT] = "it-IT"
        languageMap[checkEnUS] = "en-US"
        languageMap[checkEsES] = "es-ES"
        languageMap[checkEs419] = "es-419"
        languageMap[checkDeDE] = "de-DE"
        languageMap[checkFrFR] = "fr-FR"
        languageMap[checkPtBR] = "pt-BR"
        languageMap[checkJaJP] = "ja-JP"
        languageMap[checkRuRU] = "ru-RU"

        // Load current preferences
        loadPreferences()

        // Set up checkbox listeners to enforce max 3 languages
        languageMap.keys.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ ->
                enforceMaxLanguages()
            }
        }

        // Save button click
        btnSave.setOnClickListener {
            savePreferences()
        }
    }

    /**
     * Load current preferences from SharedPreferences
     */
    private fun loadPreferences() {
        val prefs = getSharedPreferences("crunchybadges", Activity.MODE_PRIVATE)

        // Load selected languages
        val languagesJson = prefs.getString("selectedLanguages", "[\"it-IT\"]") ?: "[\"it-IT\"]"
        try {
            val languagesArray = JSONArray(languagesJson)
            val selectedLanguages = mutableListOf<String>()
            for (i in 0 until languagesArray.length()) {
                selectedLanguages.add(languagesArray.getString(i))
            }

            // Check corresponding checkboxes
            languageMap.forEach { (checkbox, langCode) ->
                checkbox.isChecked = selectedLanguages.contains(langCode)
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to parse languages", e)
        }

        // Load filter preference
        val filterUndubbed = prefs.getBoolean("filterUndubbed", false)
        switchFilterUndubbed.isChecked = filterUndubbed
    }

    /**
     * Save preferences to SharedPreferences and finish activity
     */
    private fun savePreferences() {
        val prefs = getSharedPreferences("crunchybadges", Activity.MODE_PRIVATE)
        val editor = prefs.edit()

        // Get selected languages
        val selectedLanguages = JSONArray()
        languageMap.forEach { (checkbox, langCode) ->
            if (checkbox.isChecked) {
                selectedLanguages.put(langCode)
            }
        }

        // Validate at least one language selected
        if (selectedLanguages.length() == 0) {
            Toast.makeText(
                this,
                "Seleziona almeno una lingua",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Save languages
        editor.putString("selectedLanguages", selectedLanguages.toString())

        // Save filter preference
        editor.putBoolean("filterUndubbed", switchFilterUndubbed.isChecked)

        // Apply changes
        editor.apply()

        Toast.makeText(
            this,
            "Impostazioni salvate! Ricarica l'app per applicare i cambiamenti.",
            Toast.LENGTH_LONG
        ).show()

        // Close settings and return to main activity
        setResult(Activity.RESULT_OK)
        finish()
    }

    /**
     * Enforce maximum 3 languages selected
     */
    private fun enforceMaxLanguages() {
        val checkedCount = languageMap.keys.count { it.isChecked }

        if (checkedCount > 3) {
            // Find the checkbox that was just checked and uncheck it
            languageMap.keys.forEach { checkbox ->
                if (checkbox.isChecked && checkbox.isPressed) {
                    checkbox.isChecked = false
                }
            }

            Toast.makeText(
                this,
                "Massimo 3 lingue selezionabili",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
