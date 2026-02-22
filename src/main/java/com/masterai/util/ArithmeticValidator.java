package com.masterai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArithmeticValidator {

    private static final Logger logger = LoggerFactory.getLogger(ArithmeticValidator.class);
    
    // Pattern to match simple arithmetic like "1 + 3 = 4" or "100 * 200 = 20000"
    private static final Pattern EQUATION_PATTERN = Pattern.compile("([-+]?\\d+\\.?\\d*)\\s*([+\\-*/])\\s*([-+]?\\d+\\.?\\d*)\\s*=\\s*([-+]?\\d+\\.?\\d*)");

    public record Inconsistency(String step, String expected, String actual) {}

    /**
     * Verifies arithmetic in a list of reasoning steps.
     * Returns a list of detected inconsistencies.
     */
    public List<Inconsistency> validateSteps(List<String> steps) {
        List<Inconsistency> inconsistencies = new ArrayList<>();
        
        for (String step : steps) {
            Matcher matcher = EQUATION_PATTERN.matcher(step);
            while (matcher.find()) {
                try {
                    double operand1 = Double.parseDouble(matcher.group(1));
                    String operator = matcher.group(2);
                    double operand2 = Double.parseDouble(matcher.group(3));
                    double statedResult = Double.parseDouble(matcher.group(4));
                    
                    double calculatedResult = calculate(operand1, operand2, operator);
                    
                    // Use a small epsilon for double comparison
                    if (Math.abs(calculatedResult - statedResult) > 0.001) {
                        logger.warn("Arithmetic inconsistency detected in step: '{}'. Calculated: {}, Stated: {}", 
                                   step, calculatedResult, statedResult);
                        inconsistencies.add(new Inconsistency(step, String.valueOf(calculatedResult), String.valueOf(statedResult)));
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse equation in step: {}. Error: {}", step, e.getMessage());
                }
            }
        }
        
        return inconsistencies;
    }

    private double calculate(double a, double b, String op) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b != 0 ? a / b : Double.NaN;
            default -> Double.NaN;
        };
    }
}
