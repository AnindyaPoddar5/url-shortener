package com.example.shortener;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class LinkWriter {
    @PersistenceContext private EntityManager entityManager;
    public Link insert(Link link) {
        entityManager.persist(link);
        entityManager.flush();
        return link;
    }
}
