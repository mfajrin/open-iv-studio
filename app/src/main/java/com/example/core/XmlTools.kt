package com.example.core

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.SyntaxColors
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

data class XmlValidationResult(
  val isValid: Boolean,
  val message: String,
  val line: Int? = null,
  val column: Int? = null,
  val rootTag: String? = null,
  val elementCount: Int = 0,
)

data class XmlTreeNode(
  val id: String,
  val tagName: String,
  val attributes: List<Pair<String, String>>,
  val textValue: String?,
  val children: List<XmlTreeNode>,
  val depth: Int,
)

object XmlTools {
  fun highlightXml(
    code: String,
    searchQuery: String = "",
  ): AnnotatedString = buildAnnotatedString {
    append(code)
    val len = code.length
    var i = 0
    while (i < len) {
      when {
        code.startsWith("<!--", i) -> {
          val end = code.indexOf("-->", i + 4).let { if (it == -1) len else it + 3 }
          addStyle(
            SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic),
            i,
            end,
          )
          i = end
        }
        code.startsWith("<?", i) -> {
          val end = code.indexOf("?>", i + 2).let { if (it == -1) len else it + 2 }
          addStyle(
            SpanStyle(color = SyntaxColors.Declaration, fontWeight = FontWeight.Medium),
            i,
            end,
          )
          i = end
        }
        code[i] == '<' -> {
          val closeIdx = code.indexOf('>', i + 1).let { if (it == -1) len else it + 1 }
          styleTagSpan(code, i, closeIdx)
          i = closeIdx
        }
        else -> {
          val nextTag = code.indexOf('<', i).let { if (it == -1) len else it }
          val segment = code.substring(i, nextTag).trim()
          if (segment.isNotEmpty() && segment.all { it.isDigit() || it == '.' || it == '-' || it == ',' || it.isWhitespace() }) {
            addStyle(SpanStyle(color = SyntaxColors.NumericValue), i, nextTag)
          } else {
            addStyle(SpanStyle(color = SyntaxColors.PlainText), i, nextTag)
          }
          i = nextTag
        }
      }
    }

    if (searchQuery.isNotEmpty()) {
      var matchStart = code.indexOf(searchQuery, ignoreCase = true)
      while (matchStart >= 0) {
        val matchEnd = (matchStart + searchQuery.length).coerceAtMost(len)
        addStyle(
          SpanStyle(
            background = SyntaxColors.AttributeName.copy(alpha = 0.35f),
            color = SyntaxColors.PlainText,
            fontWeight = FontWeight.Bold,
          ),
          matchStart,
          matchEnd,
        )
        matchStart = code.indexOf(searchQuery, startIndex = matchStart + searchQuery.length.coerceAtLeast(1), ignoreCase = true)
      }
    }
  }

  private fun AnnotatedString.Builder.styleTagSpan(
    code: String,
    start: Int,
    end: Int,
  ) {
    if (start >= end) return
    var pos = start
    // Opening '<' or '</'
    val bracketEnd =
      if (pos + 1 < end && code[pos + 1] == '/') pos + 2 else (pos + 1).coerceAtMost(end)
    addStyle(SpanStyle(color = SyntaxColors.TagBracket), pos, bracketEnd)
    pos = bracketEnd

    // Tag name
    val nameStart = pos
    while (pos < end && !code[pos].isWhitespace() && code[pos] != '>' && code[pos] != '/') {
      pos++
    }
    if (pos > nameStart) {
      addStyle(
        SpanStyle(color = SyntaxColors.TagName, fontWeight = FontWeight.SemiBold),
        nameStart,
        pos,
      )
    }

    // Attributes & closing brackets
    while (pos < end) {
      val ch = code[pos]
      when {
        ch.isWhitespace() -> pos++
        ch == '/' || ch == '>' -> {
          addStyle(SpanStyle(color = SyntaxColors.TagBracket), pos, pos + 1)
          pos++
        }
        ch == '"' || ch == '\'' -> {
          val quote = ch
          val strEnd = code.indexOf(quote, pos + 1).let { if (it == -1 || it >= end) end else it + 1 }
          addStyle(SpanStyle(color = SyntaxColors.AttributeValue), pos, strEnd)
          pos = strEnd
        }
        ch == '=' -> {
          addStyle(SpanStyle(color = SyntaxColors.TagBracket), pos, pos + 1)
          pos++
        }
        else -> {
          val attrStart = pos
          while (pos < end && !code[pos].isWhitespace() && code[pos] != '=' && code[pos] != '>' && code[pos] != '/') {
            pos++
          }
          if (pos > attrStart) {
            addStyle(SpanStyle(color = SyntaxColors.AttributeName), attrStart, pos)
          } else {
            pos++
          }
        }
      }
    }
  }

  fun validateXml(xmlText: String): XmlValidationResult {
    if (xmlText.isBlank()) {
      return XmlValidationResult(isValid = false, message = "Dokumen XML kosong")
    }
    return try {
      val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      }
      val builder = factory.newDocumentBuilder()
      val doc = builder.parse(InputSource(StringReader(xmlText)))
      doc.documentElement.normalize()
      val totalElements = doc.getElementsByTagName("*").length
      XmlValidationResult(
        isValid = true,
        message = "XML Valid • Root <${doc.documentElement.nodeName}> • $totalElements elemen",
        rootTag = doc.documentElement.nodeName,
        elementCount = totalElements,
      )
    } catch (e: SAXParseException) {
      XmlValidationResult(
        isValid = false,
        message = e.message ?: "Kesalahan sintaks XML",
        line = e.lineNumber,
        column = e.columnNumber,
      )
    } catch (e: Exception) {
      XmlValidationResult(
        isValid = false,
        message = e.message ?: "Gagal memvalidasi XML",
      )
    }
  }

  fun formatXml(xmlText: String): Result<String> {
    return try {
      val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      }
      val builder = factory.newDocumentBuilder()
      val doc = builder.parse(InputSource(StringReader(xmlText)))
      doc.documentElement.normalize()
      stripWhitespaceTextNodes(doc.documentElement)

      val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        if (!xmlText.trimStart().startsWith("<?xml")) {
          setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }
      }
      val writer = StringWriter()
      transformer.transform(DOMSource(doc), StreamResult(writer))
      Result.success(writer.toString().trimEnd())
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun stripWhitespaceTextNodes(node: Node) {
    val children = node.childNodes
    val toRemove = mutableListOf<Node>()
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType == Node.TEXT_NODE && child.nodeValue.isNullOrBlank()) {
        toRemove.add(child)
      } else if (child.nodeType == Node.ELEMENT_NODE) {
        stripWhitespaceTextNodes(child)
      }
    }
    toRemove.forEach { node.removeChild(it) }
  }

  fun parseXmlTree(xmlText: String): Result<XmlTreeNode> {
    return try {
      val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      }
      val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xmlText)))
      doc.documentElement.normalize()
      var counter = 0
      fun buildNode(el: Element, depth: Int): XmlTreeNode {
        val id = "node_${counter++}"
        val attrs = mutableListOf<Pair<String, String>>()
        val attrMap = el.attributes
        for (i in 0 until attrMap.length) {
          val attr = attrMap.item(i)
          attrs.add(attr.nodeName to attr.nodeValue)
        }
        val childNodes = mutableListOf<XmlTreeNode>()
        val textParts = StringBuilder()
        val list = el.childNodes
        for (i in 0 until list.length) {
          val n = list.item(i)
          when (n.nodeType) {
            Node.ELEMENT_NODE -> childNodes.add(buildNode(n as Element, depth + 1))
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
              val t = n.nodeValue?.trim().orEmpty()
              if (t.isNotEmpty()) {
                if (textParts.isNotEmpty()) textParts.append(' ')
                textParts.append(t)
              }
            }
          }
        }
        return XmlTreeNode(
          id = id,
          tagName = el.tagName,
          attributes = attrs,
          textValue = textParts.toString().takeIf { it.isNotEmpty() },
          children = childNodes,
          depth = depth,
        )
      }
      Result.success(buildNode(doc.documentElement, 0))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

class XmlSyntaxVisualTransformation(
  private val searchQuery: String = "",
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    return TransformedText(
      text = XmlTools.highlightXml(text.text, searchQuery),
      offsetMapping = OffsetMapping.Identity,
    )
  }
}
