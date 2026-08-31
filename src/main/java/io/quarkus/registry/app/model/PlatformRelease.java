package io.quarkus.registry.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NaturalId;
import org.hibernate.type.SqlTypes;

import io.quarkus.registry.app.util.Version;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;

@Entity
@NamedQueries({
        @NamedQuery(name = "PlatformRelease.findAllCorePlatforms", query = """
                select pr from PlatformRelease pr
                where pr.platformStream.platform.platformType = 'C'
                order by pr.versionSortable desc
                """),

        @NamedQuery(name = "PlatformRelease.findByPlatformKey", query = """
                select pr from PlatformRelease pr
                where pr.platformStream.platform.platformKey = ?1 and pr.version = ?2
                """),
        @NamedQuery(name = "PlatformRelease.findQuarkusCores", query = """
                select pr.version from PlatformRelease pr
                where pr.platformStream.platform.isDefault = true
                order by pr.versionSortable desc
                """),
        @NamedQuery(name = "PlatformRelease.findLatestByQuarkusCoreVersion", query = """
                select pr from PlatformRelease pr
                where pr.quarkusCoreVersion = ?1
                and pr.unlisted = false
                and pr.platformStream.platform.platformType = 'C'
                and (pr.platformStream.id, pr.versionSortable) in (
                    select pr2.platformStream.id, max(pr2.versionSortable) from PlatformRelease pr2
                        where pr2.quarkusCoreVersion = ?1
                        and pr2.platformStream.unlisted = false
                        and pr2.unlisted = false
                        group by pr2.platformStream.id
                )
                order by pr.versionSortable desc, pr.platformStream.platform.isDefault desc
                """),
        @NamedQuery(name = "PlatformRelease.findLatest", query = """
                  select pr from PlatformRelease pr
                  where pr.unlisted = false
                  and pr.platformStream.platform.platformType = 'C'
                  and (pr.platformStream.id, pr.versionSortable) in
                      (
                       select pr2.platformStream.id, max(pr2.versionSortable) from PlatformRelease pr2
                           where pr2.platformStream.unlisted = false
                           and pr2.unlisted = false
                           group by pr2.platformStream.id
                      )
                order by pr.versionSortable desc, pr.platformStream.platform.isDefault desc
                """),
        @NamedQuery(name = "PlatformRelease.findPinned", query = """
                  select pr from PlatformRelease pr
                  where pr.unlisted = false
                  and pr.pinned = true
                  and pr.platformStream.platform.platformType = 'C'
                  order by pr.versionSortable desc, pr.platformStream.platform.isDefault desc
                """),
        @NamedQuery(name = "PlatformRelease.findLatestPinnedStream", query = """
                  select pr from PlatformRelease pr
                  where pr.unlisted = false
                  and pr.platformStream.platform.platformType = 'C'
                  and (pr.platformStream.id, pr.versionSortable) in
                      (
                       select pr2.platformStream.id, max(pr2.versionSortable) from PlatformRelease pr2
                           where pr2.platformStream.unlisted = false
                           and pr2.platformStream.pinned = true
                           and pr2.unlisted = false
                           group by pr2.platformStream.id
                      )
                order by pr.versionSortable desc, pr.platformStream.platform.isDefault desc
                """),
        @NamedQuery(name = "PlatformRelease.findByArtifactCoordinates", query = """
                select pr from PlatformRelease pr
                where pr.platformStream.platform.groupId = :groupId
                and pr.platformStream.platform.artifactId = :artifactId
                and pr.version = :version
                and pr.unlisted = false
                """),
        @NamedQuery(name = "PlatformRelease.countArtifactCoordinates", query = """
                select count(pr) from PlatformRelease pr
                where pr.platformStream.platform.groupId = :groupId
                and pr.platformStream.platform.artifactId = :artifactId
                and pr.version = :version
                and pr.unlisted = false
                """)
})
public class PlatformRelease extends BaseEntity {

    @NaturalId
    @ManyToOne
    public PlatformStream platformStream;

    @NaturalId
    @Column(nullable = false, updatable = false)
    public String version;

    /**
     * The version above formatted as a valid semver (for max and order-by operations)
     */
    @Column(updatable = false)
    private String versionSortable;

    @Column(nullable = false)
    public String quarkusCoreVersion;

    public String upstreamQuarkusCoreVersion;

    public boolean pinned;

    public boolean unlisted;

    @Column(nullable = false)
    public String bom;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<String> memberBoms = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> metadata;

    @OneToMany(mappedBy = "platformRelease", orphanRemoval = true)
    public List<PlatformExtension> extensions = new ArrayList<>();

    @OneToMany(mappedBy = "platformRelease", orphanRemoval = true)
    public List<PlatformReleaseCategory> categories = new ArrayList<>();

    public PlatformRelease() {
    }

    public PlatformRelease(PlatformStream platformStream, String version, boolean pinned) {
        this.platformStream = platformStream;
        this.version = version;
        this.pinned = pinned;
    }

    @PrePersist
    void updateSemVer() {
        this.versionSortable = Version.toSortable(version);
    }

    public boolean isVersionStable() {
        return !Version.isPreFinal(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlatformRelease platform)) {
            return false;
        }
        return Objects.equals(this.platformStream, platform.platformStream) &&
                Objects.equals(version, platform.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platformStream, version);
    }
}
