package com.ratemyuj.repository;

import com.ratemyuj.domain.Professor;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfessorRepository extends MongoRepository<Professor, String> {
}
