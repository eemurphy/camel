/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.properties;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.spi.PropertiesComponent.PREFIX_TOKEN;
import static org.apache.camel.spi.PropertiesComponent.SUFFIX_TOKEN;
import static org.apache.camel.util.IOHelper.lookupEnvironmentVariable;

/**
 * A parser to parse a string which contains property placeholders.
 */
public class DefaultPropertiesParser implements PropertiesParser {
    private static final String GET_OR_ELSE_TOKEN = ":";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private PropertiesComponent propertiesComponent;

    public DefaultPropertiesParser() {
    }

    public DefaultPropertiesParser(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = propertiesComponent;
    }

    public PropertiesComponent getPropertiesComponent() {
        return propertiesComponent;
    }

    public void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = propertiesComponent;
    }

    @Override
    public String parseUri(String text, PropertiesLookup properties, boolean defaultFallbackEnabled) throws IllegalArgumentException {
        ParsingContext context = new ParsingContext(properties, defaultFallbackEnabled);
        return context.parse(text);
    }

    @Override
    public String parseProperty(String key, String value, PropertiesLookup properties) {
        return value;
    }

    /**
     * This inner class helps replacing properties.
     */
    private final class ParsingContext {
        private final PropertiesLookup properties;
        private final boolean defaultFallbackEnabled;

        ParsingContext(PropertiesLookup properties, boolean defaultFallbackEnabled) {
            this.properties = properties;
            this.defaultFallbackEnabled = defaultFallbackEnabled;
        }

        /**
         * Parses the given input string and replaces all properties
         *
         * @param input Input string
         * @return Evaluated string
         */
        public String parse(String input) {
            return doParse(input, new HashSet<String>());
        }

        /**
         * Recursively parses the given input string and replaces all properties
         *
         * @param input                Input string
         * @param replacedPropertyKeys Already replaced property keys used for tracking circular references
         * @return Evaluated string
         */
        private String doParse(String input, Set<String> replacedPropertyKeys) {
            if (input == null) {
                return null;
            }
            String answer = input;
            Property property;
            while ((property = readProperty(answer)) != null) {
                // Check for circular references
                if (replacedPropertyKeys.contains(property.getKey())) {
                    throw new IllegalArgumentException("Circular reference detected with key [" + property.getKey() + "] from text: " + input);
                }

                Set<String> newReplaced = new HashSet<>(replacedPropertyKeys);
                newReplaced.add(property.getKey());

                String before = answer.substring(0, property.getBeginIndex());
                String after = answer.substring(property.getEndIndex());
                answer = before + doParse(property.getValue(), newReplaced) + after;
            }
            return answer;
        }

        /**
         * Finds a property in the given string. It returns {@code null} if there's no property defined.
         *
         * @param input Input string
         * @return A property in the given string or {@code null} if not found
         */
        private Property readProperty(String input) {
            // Find the index of the first valid suffix token
            int suffix = getSuffixIndex(input);

            // If not found, ensure that there is no valid prefix token in the string
            if (suffix == -1) {
                if (getMatchingPrefixIndex(input, input.length()) != -1) {
                    throw new IllegalArgumentException(String.format("Missing %s from the text: %s", SUFFIX_TOKEN, input));
                }
                return null;
            }

            // Find the index of the prefix token that matches the suffix token
            int prefix = getMatchingPrefixIndex(input, suffix);
            if (prefix == -1) {
                throw new IllegalArgumentException(String.format("Missing %s from the text: %s", PREFIX_TOKEN, input));
            }

            String key = input.substring(prefix + PREFIX_TOKEN.length(), suffix);
            String value = getPropertyValue(key, input);
            return new Property(prefix, suffix + SUFFIX_TOKEN.length(), key, value);
        }

        /**
         * Gets the first index of the suffix token that is not surrounded by quotes
         *
         * @param input Input string
         * @return First index of the suffix token that is not surrounded by quotes
         */
        private int getSuffixIndex(String input) {
            int index = -1;
            do {
                index = input.indexOf(SUFFIX_TOKEN, index + 1);
            } while (index != -1 && isQuoted(input, index, SUFFIX_TOKEN));
            return index;
        }

        /**
         * Gets the index of the prefix token that matches the suffix at the given index and that is not surrounded by quotes
         *
         * @param input       Input string
         * @param suffixIndex Index of the suffix token
         * @return Index of the prefix token that matches the suffix at the given index and that is not surrounded by quotes
         */
        private int getMatchingPrefixIndex(String input, int suffixIndex) {
            int index = suffixIndex;
            do {
                index = input.lastIndexOf(PREFIX_TOKEN, index - 1);
            } while (index != -1 && isQuoted(input, index, PREFIX_TOKEN));
            return index;
        }

        /**
         * Indicates whether or not the token at the given index is surrounded by single or double quotes
         *
         * @param input Input string
         * @param index Index of the token
         * @param token Token
         * @return {@code true}
         */
        private boolean isQuoted(String input, int index, String token) {
            int beforeIndex = index - 1;
            int afterIndex = index + token.length();
            if (beforeIndex >= 0 && afterIndex < input.length()) {
                char before = input.charAt(beforeIndex);
                char after = input.charAt(afterIndex);
                return (before == after) && (before == '\'' || before == '"');
            }
            return false;
        }

        /**
         * Gets the value of the property with given key
         *
         * @param key   Key of the property
         * @param input Input string (used for exception message if value not found)
         * @return Value of the property with the given key
         */
        private String getPropertyValue(String key, String input) {

            // the key may be a function, so lets check this first
            if (propertiesComponent != null) {
                for (PropertiesFunction function : propertiesComponent.getFunctions().values()) {
                    String token = function.getName() + ":";
                    if (key.startsWith(token)) {
                        String remainder = key.substring(token.length());
                        log.debug("Property with key [{}] is applied by function [{}]", key, function.getName());
                        String value = function.apply(remainder);
                        if (value == null) {
                            throw new IllegalArgumentException("Property with key [" + key + "] using function [" + function.getName() + "]"
                                    + " returned null value which is not allowed, from input: " + input);
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Property with key [{}] applied by function [{}] -> {}", key, function.getName(), value);
                            }
                            return value;
                        }
                    }
                }
            }

            // they key may have a get or else expression
            String defaultValue = null;
            if (defaultFallbackEnabled && key.contains(GET_OR_ELSE_TOKEN)) {
                defaultValue = StringHelper.after(key, GET_OR_ELSE_TOKEN);
                key = StringHelper.before(key, GET_OR_ELSE_TOKEN);
            }

            String value = doGetPropertyValue(key);
            if (value == null && defaultValue != null) {
                log.debug("Property with key [{}] not found, using default value: {}", key, defaultValue);
                value = defaultValue;
            }

            if (value == null) {
                StringBuilder esb = new StringBuilder();
                if (propertiesComponent == null || "true".equals(propertiesComponent.getCamelContext().getGlobalOption(PropertiesComponent.DEFAULT_CREATED))) {
                    // if the component was auto created then include more information that the end user should define it
                    esb.append("PropertiesComponent with name properties must be defined in CamelContext to support property placeholders. ");
                }
                esb.append("Property with key [").append(key).append("] ");
                esb.append("not found in properties from text: ").append(input);
                throw new IllegalArgumentException(esb.toString());
            }

            return value;
        }

        /**
         * Gets the property with the given key, it returns {@code null} if the property is not found
         *
         * @param key Key of the property
         * @return Value of the property or {@code null} if not found
         */
        private String doGetPropertyValue(String key) {
            if (ObjectHelper.isEmpty(key)) {
                return parseProperty(key, null, properties);
            }

            String value = null;

            // override is the default mode for SYS
            int sysMode = propertiesComponent != null ? propertiesComponent.getSystemPropertiesMode() : PropertiesComponent.SYSTEM_PROPERTIES_MODE_OVERRIDE;
            // fallback is the default mode for ENV
            int envMode = propertiesComponent != null ? propertiesComponent.getEnvironmentVariableMode() : PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_FALLBACK;

            if (sysMode == PropertiesComponent.SYSTEM_PROPERTIES_MODE_OVERRIDE) {
                value = System.getProperty(key);
                if (value != null) {
                    log.debug("Found a JVM system property: {} with value: {} to be used.", key, value);
                }
            }
            if (value == null && envMode == PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_OVERRIDE) {
                value = lookupEnvironmentVariable(key);
                if (value != null) {
                    log.debug("Found a environment property: {} with value: {} to be used.", key, value);
                }
            }

            if (value == null && properties != null) {
                value = properties.lookup(key);
                if (value != null) {
                    log.debug("Found property: {} with value: {} to be used.", key, value);
                }
            }

            if (value == null && sysMode == PropertiesComponent.SYSTEM_PROPERTIES_MODE_FALLBACK) {
                value = System.getProperty(key);
                if (value != null) {
                    log.debug("Found a JVM system property: {} with value: {} to be used.", key, value);
                }
            }
            if (value == null && envMode == PropertiesComponent.ENVIRONMENT_VARIABLES_MODE_FALLBACK) {
                // lookup OS env with upper case key
                value = lookupEnvironmentVariable(key);
                if (value != null) {
                    log.debug("Found a environment property: {} with value: {} to be used.", key, value);
                }
            }

            return parseProperty(key, value, properties);
        }
    }

    /**
     * This inner class is the definition of a property used in a string
     */
    private static final class Property {
        private final int beginIndex;
        private final int endIndex;
        private final String key;
        private final String value;

        private Property(int beginIndex, int endIndex, String key, String value) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
            this.key = key;
            this.value = value;
        }

        /**
         * Gets the begin index of the property (including the prefix token).
         */
        public int getBeginIndex() {
            return beginIndex;
        }

        /**
         * Gets the end index of the property (including the suffix token).
         */
        public int getEndIndex() {
            return endIndex;
        }

        /**
         * Gets the key of the property.
         */
        public String getKey() {
            return key;
        }

        /**
         * Gets the value of the property.
         */
        public String getValue() {
            return value;
        }
    }
}
