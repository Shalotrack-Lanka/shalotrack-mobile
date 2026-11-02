package com.example.letstracklanka.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InputValidatorTest {

    @Test public void name_acceptsSinhalaTamilAndLatin() {
        assertNull(InputValidator.name("Nimal Perera"));
        assertNull(InputValidator.name("O'Neil-Silva Jr."));
        assertNull(InputValidator.name("නිමල් පෙරේරා"));
        assertNull(InputValidator.name("கமலா தேவி"));
    }

    @Test public void name_rejectsDigitsMarkupAndTooShortOrLong() {
        assertNotNull(InputValidator.name("A"));
        assertNull(InputValidator.name("ABC Traders (Pvt) Ltd"));
        assertNotNull(InputValidator.name("123 Bob"));
        assertNotNull(InputValidator.name("<script>alert(1)</script>"));
        assertNotNull(InputValidator.name("Bob; DROP TABLE users"));
        assertNotNull(InputValidator.name("x".repeat(81)));
        assertNotNull(InputValidator.name(null));
    }

    @Test public void clean_removesInvisibleAndBidiButKeepsSinhalaJoiner() {
        assertEquals("abc", InputValidator.clean("a​b‮c"));
        assertEquals("a b", InputValidator.clean("  a \t  b  "));
        assertEquals("ක‍ර", InputValidator.clean("ක‍ර"));
    }

    @Test public void nic_acceptsOldAndNewAndCanonicalises() {
        assertNull(InputValidator.nic("861234567v"));
        assertNull(InputValidator.nic("198612345678"));
        assertEquals("861234567V", InputValidator.canonicalNic(" 861234 567v "));
        assertNotNull(InputValidator.nic("12345"));
        assertNotNull(InputValidator.nic("86123456789"));
    }

    @Test public void email_rules() {
        assertNull(InputValidator.email("a.b@gmail.com"));
        assertNotNull(InputValidator.email("a b@gmail.com"));
        assertNotNull(InputValidator.email("nope"));
        assertNotNull(InputValidator.email("a@b.com" + "m".repeat(150)));
    }

    @Test public void plate_rules() {
        assertNull(InputValidator.plate("cab-1234"));
        assertNull(InputValidator.plate("WP CAB 1234"));
        assertNotNull(InputValidator.plate("<b>"));
        assertNotNull(InputValidator.plate("A"));
    }

    @Test public void imei_requiresLuhn() {
        assertNull(InputValidator.imei("490154203237518"));      // well-known valid test IMEI
        assertNotNull(InputValidator.imei("490154203237519"));
        assertNotNull(InputValidator.imei("12345"));
    }

    @Test public void message_blocksMarkupAndOversize() {
        assertNull(InputValidator.message("The tracker stopped reporting yesterday evening.", 10));
        assertNotNull(InputValidator.message("hello <img src=x onerror=alert(1)> world", 5));
        assertNotNull(InputValidator.message("click javascript:alert(1)", 5));
        assertNotNull(InputValidator.message("short", 10));
        assertNotNull(InputValidator.message("x".repeat(1001), 5));
        assertNull(InputValidator.message("price is 5 < 10 and 20 > 15 today", 5));
    }

    @Test public void label_rejectsAngleBrackets() {
        assertNull(InputValidator.label("Home", 1, 50, "a place name"));
        assertNotNull(InputValidator.label("", 1, 50, "a place name"));
        assertNotNull(InputValidator.label("a<b", 1, 50, "a place name"));
        assertNotNull(InputValidator.label("x".repeat(51), 1, 50, "a place name"));
    }

    @Test public void phone_and_otp_and_range() {
        assertNull(InputValidator.phone("077 123 4567"));
        assertNull(InputValidator.phone("+94771234567"));
        assertNotNull(InputValidator.phone("0112345678"));
        assertNull(InputValidator.otp("123456"));
        assertNotNull(InputValidator.otp("12345a"));
        assertNull(InputValidator.inRange(100, 50, 5000, "radius"));
        assertNotNull(InputValidator.inRange(10, 50, 5000, "radius"));
        assertNotNull(InputValidator.inRange(Double.NaN, 50, 5000, "radius"));
    }

    @Test public void password_policy() {
        assertNull(InputValidator.password("Tr4ckmeNow", "a@b.com"));
        assertNotNull(InputValidator.password("short1", null));
        assertNotNull(InputValidator.password("alllettersonly", null));
        assertNotNull(InputValidator.password("123456789012", null));
        assertNotNull(InputValidator.password("Password1", null));
        assertNotNull(InputValidator.password("nimalperera1", "nimalperera1@gmail.com"));
        assertNotNull(InputValidator.password("a1".repeat(70), null));
    }
}
