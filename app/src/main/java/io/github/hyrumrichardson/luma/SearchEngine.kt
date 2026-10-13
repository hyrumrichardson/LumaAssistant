package io.github.hyrumrichardson.luma

import java.net.URLEncoder

/** Where web searches go. They open in the phone's default browser. */
enum class SearchEngine(val label: String, private val urlPrefix: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
    ;

    fun urlFor(query: String): String = urlPrefix + URLEncoder.encode(query, Charsets.UTF_8.name())
}
