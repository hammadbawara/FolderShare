package com.hz_apps.foldershare.core.explorer

import com.hz_apps.foldershare.core.explorer.parser.WebDavXmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebDavXmlParserTest {

    @Test
    fun testParsePropfindMultistatusResponse() {
        val xmlContent = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/shared/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getlastmodified>Sat, 01 Aug 2026 07:00:00 GMT</D:getlastmodified>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/shared/Photos</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getlastmodified>Sat, 01 Aug 2026 07:05:00 GMT</D:getlastmodified>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/shared/sample.pdf</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getlastmodified>Sat, 01 Aug 2026 07:10:00 GMT</D:getlastmodified>
                    <D:resourcetype/>
                    <D:getcontentlength>2048576</D:getcontentlength>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val result = WebDavXmlParser.parsePropfindResponse(xmlContent, "/shared")
        val files = result.files

        assertEquals(2, files.size)
        assertTrue(result.isWriteAllowed)

        // Folders come first
        val folder = files[0]
        assertEquals("Photos", folder.name)
        assertEquals("/shared/Photos", folder.path)
        assertTrue(folder.isDirectory)
        assertEquals("Folder", folder.formattedSize)

        // Files come second
        val file = files[1]
        assertEquals("sample.pdf", file.name)
        assertEquals("/shared/sample.pdf", file.path)
        assertEquals(false, file.isDirectory)
        assertEquals(2048576L, file.size)
        assertEquals("2.0 MB", file.formattedSize)
        assertEquals("pdf", file.extension)
    }

    @Test
    fun testParseEncodedHrefWithSpaces() {
        val xmlContent = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/My%20Shared%20Folder/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val result = WebDavXmlParser.parsePropfindResponse(xmlContent, "/")
        val files = result.files
        assertEquals(1, files.size)

        val folder = files[0]
        assertEquals("My Shared Folder", folder.name)
        assertEquals("/My Shared Folder", folder.path)
        assertTrue(folder.isDirectory)
    }

    @Test
    fun testParsePropfindReadOnlyPermissions() {
        val xmlContent = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/readonly/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:current-user-privilege-set>
                      <D:privilege><D:read/></D:privilege>
                    </D:current-user-privilege-set>
                    <D:isreadonly>1</D:isreadonly>
                    <D:iswriteallowed>0</D:iswriteallowed>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/readonly/file.txt</D:href>
                <D:propstat>
                  <D:prop>
                    <D:iswriteallowed>0</D:iswriteallowed>
                    <D:resourcetype/>
                    <D:getcontentlength>100</D:getcontentlength>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val result = WebDavXmlParser.parsePropfindResponse(xmlContent, "/readonly")
        kotlin.test.assertFalse(result.isWriteAllowed)
        assertEquals(1, result.files.size)
        kotlin.test.assertFalse(result.files[0].isWriteAllowed)
    }
}
