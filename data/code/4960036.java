// ***************************************************************************************************************************
// * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file *
// * distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file        *
// * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance            *
// * with the License.  You may obtain a copy of the License at                                                              *
// *                                                                                                                         *
// *  http://www.apache.org/licenses/LICENSE-2.0                                                                             *
// *                                                                                                                         *
// * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an  *
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the        *
// * specific language governing permissions and limitations under the License.                                              *
// ***************************************************************************************************************************
package org.apache.juneau.json;

import static org.apache.juneau.jsonschema.JsonSchemaGenerator.*;

import java.util.*;

import org.apache.juneau.*;
import org.apache.juneau.annotation.*;
import org.apache.juneau.http.*;
import org.apache.juneau.jsonschema.*;
import org.apache.juneau.serializer.*;

/**
 * Builder class for building instances of JSON Schema serializers.
 */
public class JsonSchemaSerializerBuilder extends JsonSerializerBuilder {

	/**
	 * Constructor, default settings.
	 */
	public JsonSchemaSerializerBuilder() {
		super();
	}

	/**
	 * Constructor.
	 *
	 * @param ps The initial configuration settings for this builder.
	 */
	public JsonSchemaSerializerBuilder(PropertyStore ps) {
		super(ps);
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializer build() {
		return build(JsonSchemaSerializer.class);
	}


	//-----------------------------------------------------------------------------------------------------------------
	// Properties
	//-----------------------------------------------------------------------------------------------------------------

	/**
	 * Configuration property:  Add descriptions.
	 *
	 * <p>
	 * Identifies which categories of types that descriptions should be automatically added to generated schemas.
	 * <p>
	 * The description is the result of calling {@link ClassMeta#getReadableName()}.
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_addDescriptionsTo}
	 * </ul>
	 *
	 * @param value
	 * 	The new value for this property.
	 * 	<br>The default is <jk>false</jk>.
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder addDescriptionsTo(String value) {
		return set(JSONSCHEMA_addDescriptionsTo, value);
	}

	/**
	 * Configuration property:  Add examples.
	 *
	 * <p>
	 * Identifies which categories of types that examples should be automatically added to generated schemas.
	 * <p>
	 * The examples come from calling {@link ClassMeta#getExample(BeanSession)} which in turn gets examples
	 * from the following:
	 * <ul class='doctree'>
	 * 	<li class='ja'>{@link Example}
	 * 	<li class='jf'>{@link BeanContext#BEAN_examples}
	 * </ul>
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_addExamplesTo}
	 * </ul>
	 *
	 * @param value
	 * 	The new value for this property.
	 * 	<br>The default is <jk>false</jk>.
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder addExamplesTo(String value) {
		return set(JSONSCHEMA_addExamplesTo, value);
	}

	/**
	 * Configuration property:  Allow nested descriptions.
	 *
	 * <p>
	 * Identifies whether nested descriptions are allowed in schema definitions.
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_allowNestedDescriptions}
	 * </ul>
	 *
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder allowNestedDescriptions() {
		return set(JSONSCHEMA_allowNestedDescriptions, true);
	}

	/**
	 * Configuration property:  Allow nested examples.
	 *
	 * <p>
	 * Identifies whether nested examples are allowed in schema definitions.
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_allowNestedExamples}
	 * </ul>
	 *
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder allowNestedExamples() {
		return set(JSONSCHEMA_allowNestedExamples, true);
	}

	/**
	 * Configuration property:  Schema definition mapper.
	 *
	 * <p>
	 * Interface to use for converting Bean classes to definition IDs and URIs.
	 * <p>
	 * Used primarily for defining common definition sections for beans in Swagger JSON.
	 * <p>
	 * This setting is ignored if {@link JsonSchemaGenerator#JSONSCHEMA_useBeanDefs} is not enabled.
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_beanDefMapper}
	 * </ul>
	 *
	 * @param value
	 * 	The new value for this property.
	 * 	<br>The default is <jk>false</jk>.
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder beanDefMapper(Class<? extends BeanDefMapper> value) {
		return set(JSONSCHEMA_beanDefMapper, value);
	}

	/**
	 * Configuration property:  Bean schema definition mapper.
	 *
	 * <p>
	 * Interface to use for converting Bean classes to definition IDs and URIs.
	 * <p>
	 * Used primarily for defining common definition sections for beans in Swagger JSON.
	 * <p>
	 * This setting is ignored if {@link JsonSchemaGenerator#JSONSCHEMA_useBeanDefs} is not enabled.
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_beanDefMapper}
	 * </ul>
	 *
	 * @param value
	 * 	The new value for this property.
	 * 	<br>The default is <jk>false</jk>.
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder beanDefMapper(BeanDefMapper value) {
		return set(JSONSCHEMA_beanDefMapper, value);
	}

	/**
	 * Configuration property:  Default schemas.
	 *
	 * <p>
	 * Allows you to override or provide custom schema information for particular class types.
	 * <p>
	 * Keys are full class names.
	 *
	 * <h5 class='section'>See Also:</h5>
	 * <ul>
	 * 	<li class='jf'>{@link JsonSchemaGenerator#JSONSCHEMA_defaultSchemas}
	 * </ul>
	 *
	 * @param c
	 * 	The class to define a default schema for.
	 * @param schema
	 * 	The schema.
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder defaultSchema(Class<?> c, ObjectMap schema) {
		return addTo(JSONSCHEMA_defaultSchemas, c.getName(), schema);
	}

	/**
	 * Configuration property:  Use bean definitions.
	 *
	 * <p>
	 * When enabled, schemas on beans will be serialized as the following:
	 * <p class='bcode w800'>
	 * 	{
	 * 		type: <js>'object'</js>,
	 * 		<js>'$ref'</js>: <js>'#/definitions/TypeId'</js>
	 * 	}
	 * </p>
	 *
	 * @return This object (for method chaining).
	 */
	public JsonSchemaSerializerBuilder useBeanDefs() {
		return set(JSONSCHEMA_useBeanDefs, true);
	}

	@Override /* JsonSerializerBuilder */
	public JsonSchemaSerializerBuilder escapeSolidus(boolean value) {
		super.escapeSolidus(value);
		return this;
	}

	@Override /* JsonSerializerBuilder */
	public JsonSchemaSerializerBuilder escapeSolidus() {
		super.escapeSolidus();
		return this;
	}

	@Override /* JsonSerializerBuilder */
	public JsonSchemaSerializerBuilder simple(boolean value) {
		super.simple(value);
		return this;
	}

	@Override /* JsonSerializerBuilder */
	public JsonSchemaSerializerBuilder simple() {
		super.simple();
		return this;
	}

	@Override /* JsonSerializerBuilder */
	public JsonSchemaSerializerBuilder ssq() {
		super.ssq();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder addBeanTypes(boolean value) {
		super.addBeanTypes(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder addBeanTypes() {
		super.addBeanTypes();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder addRootType(boolean value) {
		super.addRootType(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder addRootType() {
		super.addRootType();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder detectRecursions(boolean value) {
		super.detectRecursions(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder detectRecursions() {
		super.detectRecursions();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder ignoreRecursions(boolean value) {
		super.ignoreRecursions(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder ignoreRecursions() {
		super.ignoreRecursions();
		return this;
	}
	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder initialDepth(int value) {
		super.initialDepth(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder listener(Class<? extends SerializerListener> value) {
		super.listener(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder maxDepth(int value) {
		super.maxDepth(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder maxIndent(int value) {
		super.maxIndent(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder quoteChar(char value) {
		super.quoteChar(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder sortCollections(boolean value) {
		super.sortCollections(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder sortCollections() {
		super.sortCollections();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder sortMaps(boolean value) {
		super.sortMaps(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder sortMaps() {
		super.sortMaps();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder sq() {
		super.sq();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimEmptyCollections(boolean value) {
		super.trimEmptyCollections(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimEmptyCollections() {
		super.trimEmptyCollections();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimEmptyMaps(boolean value) {
		super.trimEmptyMaps(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimEmptyMaps() {
		super.trimEmptyMaps();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimNullProperties(boolean value) {
		super.trimNullProperties(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimStrings(boolean value) {
		super.trimStrings(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder trimStrings() {
		super.trimStrings();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder uriContext(UriContext value) {
		super.uriContext(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder uriRelativity(UriRelativity value) {
		super.uriRelativity(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder uriResolution(UriResolution value) {
		super.uriResolution(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder useWhitespace(boolean value) {
		super.useWhitespace(value);
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder useWhitespace() {
		super.useWhitespace();
		return this;
	}

	@Override /* SerializerBuilder */
	public JsonSchemaSerializerBuilder ws() {
		super.ws();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanClassVisibility(Visibility value) {
		super.beanClassVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanConstructorVisibility(Visibility value) {
		super.beanConstructorVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanDictionary(boolean append, Object...values) {
		super.beanDictionary(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanDictionary(Class<?>...values) {
		super.beanDictionary(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanDictionary(Object...values) {
		super.beanDictionary(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanDictionaryRemove(Object...values) {
		super.beanDictionaryRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanFieldVisibility(Visibility value) {
		super.beanFieldVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanFilters(boolean append, Object...values) {
		super.beanFilters(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanFilters(Class<?>...values) {
		super.beanFilters(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanFilters(Object...values) {
		super.beanFilters(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanFiltersRemove(Object...values) {
		super.beanFiltersRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanMapPutReturnsOldValue(boolean value) {
		super.beanMapPutReturnsOldValue(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanMapPutReturnsOldValue() {
		super.beanMapPutReturnsOldValue();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanMethodVisibility(Visibility value) {
		super.beanMethodVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireDefaultConstructor(boolean value) {
		super.beansRequireDefaultConstructor(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireDefaultConstructor() {
		super.beansRequireDefaultConstructor();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireSerializable(boolean value) {
		super.beansRequireSerializable(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireSerializable() {
		super.beansRequireSerializable();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireSettersForGetters(boolean value) {
		super.beansRequireSettersForGetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireSettersForGetters() {
		super.beansRequireSettersForGetters();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beansRequireSomeProperties(boolean value) {
		super.beansRequireSomeProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder beanTypePropertyName(String value) {
		super.beanTypePropertyName(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder debug() {
		super.debug();
		return this;
	}

	@Override /* BeanContextBuilder */
	public <T> JsonSchemaSerializerBuilder example(Class<T> c, T o) {
		super.example(c, o);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreInvocationExceptionsOnGetters(boolean value) {
		super.ignoreInvocationExceptionsOnGetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreInvocationExceptionsOnGetters() {
		super.ignoreInvocationExceptionsOnGetters();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreInvocationExceptionsOnSetters(boolean value) {
		super.ignoreInvocationExceptionsOnSetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreInvocationExceptionsOnSetters() {
		super.ignoreInvocationExceptionsOnSetters();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignorePropertiesWithoutSetters(boolean value) {
		super.ignorePropertiesWithoutSetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreUnknownBeanProperties(boolean value) {
		super.ignoreUnknownBeanProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreUnknownBeanProperties() {
		super.ignoreUnknownBeanProperties();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder ignoreUnknownNullBeanProperties(boolean value) {
		super.ignoreUnknownNullBeanProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public <T> JsonSchemaSerializerBuilder implClass(Class<T> interfaceClass, Class<? extends T> implClass) {
		super.implClass(interfaceClass, implClass);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder implClasses(Map<String,Class<?>> values) {
		super.implClasses(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder locale(Locale value) {
		super.locale(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder mediaType(MediaType value) {
		super.mediaType(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanClasses(boolean append, Object...values) {
		super.notBeanClasses(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanClasses(Class<?>...values) {
		super.notBeanClasses(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanClasses(Object...values) {
		super.notBeanClasses(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanClassesRemove(Object...values) {
		super.notBeanClassesRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanPackages(boolean append, Object...values) {
		super.notBeanPackages(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanPackages(Object...values) {
		super.notBeanPackages(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanPackages(String...values) {
		super.notBeanPackages(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder notBeanPackagesRemove(Object...values) {
		super.notBeanPackagesRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder pojoSwaps(boolean append, Object...values) {
		super.pojoSwaps(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder pojoSwaps(Class<?>...values) {
		super.pojoSwaps(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder pojoSwaps(Object...values) {
		super.pojoSwaps(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder pojoSwapsRemove(Object...values) {
		super.pojoSwapsRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder sortProperties(boolean value) {
		super.sortProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder sortProperties() {
		super.sortProperties();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder timeZone(TimeZone value) {
		super.timeZone(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder useEnumNames() {
		super.useEnumNames();
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder useInterfaceProxies(boolean value) {
		super.useInterfaceProxies(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder useJavaBeanIntrospector(boolean value) {
		super.useJavaBeanIntrospector(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public JsonSchemaSerializerBuilder useJavaBeanIntrospector() {
		super.useJavaBeanIntrospector();
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder set(String name, Object value) {
		super.set(name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder set(boolean append, String name, Object value) {
		super.set(append, name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder set(Map<String,Object> properties) {
		super.set(properties);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder add(Map<String,Object> properties) {
		super.add(properties);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder addTo(String name, Object value) {
		super.addTo(name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder addTo(String name, String key, Object value) {
		super.addTo(name, key, value);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder removeFrom(String name, Object value) {
		super.removeFrom(name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public JsonSchemaSerializerBuilder apply(PropertyStore copyFrom) {
		super.apply(copyFrom);
		return this;
	}
}