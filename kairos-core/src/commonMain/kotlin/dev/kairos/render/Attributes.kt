package dev.kairos.render

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Lets an author declare any HTML attribute as a Kotlin property, once:
 *
 * `
 * var ElementNode.dataKairosId: String by attribute("data-kairos-id")
 * `
 *
 * Assigning an empty string removes the attribute, so optional attributes are just nullable
 * logic at the call site: `if (url.isNotEmpty()) href = url`.
 */
class AttributeDelegate(
    private val name: String,
    private val fallback: String = "",
) : ReadWriteProperty<ElementNode, String> {

    override fun getValue(thisRef: ElementNode, property: KProperty<*>): String =
        thisRef.attributes[name] ?: fallback

    override fun setValue(thisRef: ElementNode, property: KProperty<*>, value: String) {
        if (value.isEmpty()) thisRef.attributes.remove(name) else thisRef.attributes[name] = value
    }
}

fun attribute(name: String, fallback: String = ""): AttributeDelegate = AttributeDelegate(name, fallback)

/** Boolean attributes: present means on. */
class FlagAttributeDelegate(private val name: String) : ReadWriteProperty<ElementNode, Boolean> {
    override fun getValue(thisRef: ElementNode, property: KProperty<*>): Boolean =
        thisRef.attributes.containsKey(name)

    override fun setValue(thisRef: ElementNode, property: KProperty<*>, value: Boolean) {
        if (value) thisRef.attributes[name] = name else thisRef.attributes.remove(name)
    }
}

fun flagAttribute(name: String): FlagAttributeDelegate = FlagAttributeDelegate(name)

// -- attributes that are common to both libraries -----------------------------

var ElementNode.href: String by attribute("href")
var ElementNode.src: String by attribute("src")
var ElementNode.rel: String by attribute("rel")
var ElementNode.type: String by attribute("type")
var ElementNode.name: String by attribute("name")
var ElementNode.value: String by attribute("value")
var ElementNode.placeholder: String by attribute("placeholder")
var ElementNode.method: String by attribute("method")
var ElementNode.action: String by attribute("action")
var ElementNode.title: String by attribute("title")
var ElementNode.lang: String by attribute("lang")
var ElementNode.charset: String by attribute("charset")
var ElementNode.content: String by attribute("content")
var ElementNode.role: String by attribute("role")
var ElementNode.forId: String by attribute("for")
var ElementNode.ariaLabel: String by attribute("aria-label")
var ElementNode.ariaHidden: String by attribute("aria-hidden")
