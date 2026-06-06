package com.example.groceriestracker.ai

import android.graphics.Bitmap
import com.example.groceriestracker.data.model.Item
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class GeminiVisionManager {

    suspend fun auditPantry(
        bitmap: Bitmap,
        apiKey: String,
        knownItems: List<Item>,
        modelName: String = "gemini-3.5-flash"
    ): List<AuditResult> {
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val knownItemsStr = if (knownItems.isEmpty()) {
            "(None yet)"
        } else {
            knownItems.joinToString("\n") { "- ${it.name} (Category: ${it.category})" }
        }

        val prompt = """
            Analyze this image of a fridge/pantry. Identify all items and their estimated quantities. Compare this against a provided list of 'known' items. Return a JSON array detailing what is present, what is new, and what known items appear to be missing. 
            Example format: `[{"item": "June Shine", "status": "missing", "action": "add_to_shopping_list"}, {"item": "Spicy Italian Sausage", "status": "in_stock", "quantity": 2}, {"item": "Mozz's Cat Food", "status": "low_stock", "quantity": 1}]`.
            
            Return ONLY the raw JSON array. Do not wrap it in markdown code blocks or add any other text outside the JSON.
            
            List of Known Items:
            $knownItemsStr
        """.trimIndent()

        val inputContent = content {
            image(bitmap)
            text(prompt)
        }

        val response = generativeModel.generateContent(inputContent)
        val rawResponseText = response.text ?: throw Exception("Empty response from Gemini")
        
        return parseAuditResults(rawResponseText)
    }

    fun parseAuditResults(rawJson: String): List<AuditResult> {
        val cleanedJson = cleanJsonResponse(rawJson)
        val list = mutableListOf<AuditResult>()
        try {
            val jsonArray = JSONArray(cleanedJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("item", "").trim()
                if (name.isEmpty()) continue
                
                val status = obj.optString("status", "in_stock")
                val action = obj.optString("action", "")
                val quantity = obj.optInt("quantity", if (status == "missing") 0 else 1)
                
                list.add(
                    AuditResult(
                        itemName = name,
                        status = status,
                        action = action,
                        quantity = quantity
                    )
                )
            }
        } catch (e: JSONException) {
            throw Exception("Failed to parse Gemini response as JSON: ${e.message}\nRaw response was:\n$rawJson", e)
        }
        return list
    }

    private fun cleanJsonResponse(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```")
            if (cleaned.startsWith("json", ignoreCase = true)) {
                cleaned = cleaned.substringAfter("json")
            }
            // Strip any leading whitespace or newlines after the backticks
            cleaned = cleaned.trim()
            if (cleaned.contains("```")) {
                cleaned = cleaned.substringBeforeLast("```")
            }
        }
        return cleaned.trim()
    }
}

data class AuditResult(
    val itemName: String,
    val status: String, // "missing", "in_stock", "low_stock", etc.
    val action: String, // e.g. "add_to_shopping_list"
    val quantity: Int
)
