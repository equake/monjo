package org.pojongo.core.conversion;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.hibernate.cfg.NamingStrategy;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Default implementation of <code>DocumentToObjectConverter</code>.
 * 
 * @author Caio Filipini
 * @see org.pojongo.core.conversion.DocumentToObjectConverter
 */
public class DefaultDocumentToObjectConverter implements
		DocumentToObjectConverter {

	// private final Mirror mirror;
	private DBObject document;
	private NamingStrategy namingStrategy;

	/**
	 * Default constructor.
	 */
	DefaultDocumentToObjectConverter() {
		// this.mirror = new Mirror();
	}

	DefaultDocumentToObjectConverter(NamingStrategy namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	@Override
	public DefaultDocumentToObjectConverter from(final DBObject document) {
		if (document == null) {
			throw new IllegalArgumentException("cannot convert a null document");
		}
		this.document = document;
		return this;
	}

	@Override
	public <T extends Object> T to(final Class<T> objectType)
			throws Exception {
		if (document == null) {
			throw new IllegalStateException(
					"cannot convert a null document, please call from(DBObject) first!");
		}

		T instance = instanceFor(objectType);
		PropertyDescriptor[] desc = getFieldsFor(objectType);

		for (PropertyDescriptor property : desc) {
			String field = property.getName();
			Object fieldValue = null;
			if ("class".equals(field))
				continue;
			if ("id".equals(field)) {
				fieldValue = document.get("_id");
			} else {
				if (property.getReadMethod().isAnnotationPresent(
						Transient.class)) {
					continue;
				}
				field = namingStrategy.propertyToColumnName(field);
				if (document.containsField(field)) {
					fieldValue = document.get(field);
					if (fieldValue instanceof BasicDBObject){
						BasicDBObject basicDBObject = (BasicDBObject) fieldValue;
						Class<?> innerEntityClass = Class.forName((String) basicDBObject.get("$ref"));
						DefaultDocumentToObjectConverter converter = new DefaultDocumentToObjectConverter(namingStrategy);
						fieldValue = converter.from(basicDBObject).to(innerEntityClass);
					}
					
					
				}
			}
			// BeanUtilsBean.getInstance().setProperty(instance, field,
			// fieldValue);
			if (!property.getPropertyType().isInstance(fieldValue)) {
				fieldValue = ConvertUtils.convert(fieldValue,
						property.getPropertyType());
			} 
			Method writeMethod = property.getWriteMethod();
			if (writeMethod == null) {
				throw new RuntimeException(
						"Tentativa de acessar propriedade somente para leitura");
			}
			try {
				writeMethod.invoke(instance, fieldValue);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return instance;
	}

	private <T> PropertyDescriptor[] getFieldsFor(final Class<T> objectType) {
		try {
			return PropertyUtils.getPropertyDescriptors(objectType);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private <T> T instanceFor(final Class<T> objectType) {
		if (objectType == null)
			throw new IllegalArgumentException();
		try {
			return objectType.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setNamingStrategy(NamingStrategy namingStrategy) {
		this.namingStrategy = namingStrategy;
	}
}
