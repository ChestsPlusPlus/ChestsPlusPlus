package com.jamesdpeters.minecraft.database.hibernate;

import lombok.experimental.UtilityClass;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.io.File;
import java.util.Arrays;
import java.util.Properties;

@UtilityClass
public class HibernateUtil {

    private static SessionFactory sessionFactory;
    private static EntityManager entityManager;

    private static File databaseDirectory;

    private static Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.put(Environment.DRIVER, "org.h2.Driver");
        properties.put(Environment.URL, "jdbc:h2:file:"+databaseDirectory.getAbsolutePath()+"/data/database");
        properties.put(Environment.USER, "sa");
        properties.put(Environment.PASS, "sa");
        properties.put(Environment.FORMAT_SQL, "true");
        properties.put(Environment.DIALECT, "org.hibernate.dialect.H2Dialect");
        properties.put(Environment.SHOW_SQL, "true");
        properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        properties.put(Environment.HBM2DDL_AUTO, "update");
        //        properties.put(Environment.POOL_SIZE, "5");
        return properties;
    }

    private static SessionFactory buildSessionFactory(Class<?>[] entities) {
        try {
            var config = new Configuration()
                    .addProperties(hibernateProperties())
                    .addPackage("com.jamesdpeters.minecraft.database.entities");
            Arrays.stream(entities).forEach(config::addAnnotatedClass);
            return config.buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("build SeesionFactory failed :" + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public void init(File databaseDirectory, Class<?>[] entities) {
        HibernateUtil.databaseDirectory = databaseDirectory;
        sessionFactory = buildSessionFactory(entities);
        entityManager = sessionFactory.createEntityManager();
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public <T> CriteriaQuery<T> getCriteriaQuery(Class<T> clazz) {
        return entityManager.getCriteriaBuilder().createQuery(clazz);
    }

    public CriteriaBuilder getCriteriaBuilder() {
        return entityManager.getCriteriaBuilder();
    }

    public void close() {
        if (sessionFactory != null)
            sessionFactory.close();
        if (entityManager != null)
            entityManager.close();
    }

}
