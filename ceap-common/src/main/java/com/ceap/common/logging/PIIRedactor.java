package com.ceap.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for redacting Personally Identifiable Information (PII) from logs.
 * 
 * This class provides methods to mask sensitive data before logging to comply
 * with privacy requirements.
 * 
 * Requirements:
 * - Req 18.4: PII redaction in logs
 */
public class PIIRedactor {
    
    // Email pattern: user@domain.com -> u***@d***.com
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    );
    
    // Phone pattern: +1-555-1234 or (555) 123-4567 or 555-123-4567
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+?\\d{1,3}[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}"
    );
    
    // Credit card pattern: 1234-5678-9012-3456
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}\\b"
    );
    
    // SSN pattern: 123-45-6789
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );
    
    private PIIRedactor() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Redacts all PII from a string.
     * 
     * @param text The text to redact
     * @return The redacted text
     */
    public static String redactAll(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String result = text;
        result = redactEmail(result);
        result = redactPhone(result);
        result = redactCreditCard(result);
        result = redactSSN(result);
        
        return result;
    }
    
    /**
     * Redacts email addresses from a string.
     * Pattern: user@domain.com -> u***@d***.com
     * 
     * @param text The text to redact
     * @return The redacted text
     */
    public static String redactEmail(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String user = matcher.group(1);
            String domain = matcher.group(2);
            
            String redactedUser = user.length() > 1 
                ? user.charAt(0) + "***" 
                : "***";
            
            String redactedDomain = domain.length() > 4
                ? domain.charAt(0) + "***" + domain.substring(domain.lastIndexOf('.'))
                : "***";
            
            matcher.appendReplacement(sb, redactedUser + "@" + redactedDomain);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Redacts phone numbers from a string.
     * Pattern: +1-555-1234 -> +1-***-****
     * 
     * @param text The text to redact
     * @return The redacted text
     */
    public static String redactPhone(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        Matcher matcher = PHONE_PATTERN.matcher(text);
        return matcher.replaceAll("***-***-****");
    }
    
    /**
     * Redacts credit card numbers from a string.
     * Pattern: 1234-5678-9012-3456 -> ****-****-****-3456
     * 
     * @param text The text to redact
     * @return The redacted text
     */
    public static String redactCreditCard(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        Matcher matcher = CREDIT_CARD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String card = matcher.group();
            String digits = card.replaceAll("[^0-9]", "");
            String lastFour = digits.substring(digits.length() - 4);
            matcher.appendReplacement(sb, "****-****-****-" + lastFour);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Redacts Social Security Numbers from a string.
     * Pattern: 123-45-6789 -> ***-**-****
     * 
     * @param text The text to redact
     * @return The redacted text
     */
    public static String redactSSN(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        Matcher matcher = SSN_PATTERN.matcher(text);
        return matcher.replaceAll("***-**-****");
    }
    
    /**
     * Redacts customer ID by keeping first 4 characters and masking the rest.
     * Pattern: CUST123456789 -> CUST******
     * 
     * @param customerId The customer ID to redact
     * @return The redacted customer ID
     */
    public static String redactCustomerId(String customerId) {
        if (customerId == null || customerId.isEmpty()) {
            return customerId;
        }
        
        if (customerId.length() <= 4) {
            return "****";
        }
        
        return customerId.substring(0, 4) + "******";
    }
    
    /**
     * Redacts an address by replacing it with a placeholder.
     * 
     * @param address The address to redact
     * @return A redacted placeholder
     */
    public static String redactAddress(String address) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        
        return "[ADDRESS_REDACTED]";
    }
    
    /**
     * Redacts a name by keeping first initial and masking the rest.
     * Pattern: John Doe -> J*** D***
     * 
     * @param name The name to redact
     * @return The redacted name
     */
    public static String redactName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        String[] parts = name.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            
            String part = parts[i];
            if (part.length() > 0) {
                result.append(part.charAt(0)).append("***");
            }
        }
        
        return result.toString();
    }
}
