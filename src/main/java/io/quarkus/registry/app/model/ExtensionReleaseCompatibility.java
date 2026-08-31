package io.quarkus.registry.app.model;

import org.hibernate.annotations.NaturalId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;

@Entity
@NamedQuery(name = "ExtensionReleaseCompatibility.findCompatibility", query = """
        select e.extensionRelease.id as id, e.compatible as compatible from ExtensionReleaseCompatibility e
        where e.quarkusCoreVersion = :quarkusCore
        """)
public class ExtensionReleaseCompatibility extends BaseEntity {

    @NaturalId
    @ManyToOne(optional = false)
    public ExtensionRelease extensionRelease;

    @NaturalId
    @Column(nullable = false)
    public String quarkusCoreVersion;

    @Column(nullable = false)
    public boolean compatible;

}
