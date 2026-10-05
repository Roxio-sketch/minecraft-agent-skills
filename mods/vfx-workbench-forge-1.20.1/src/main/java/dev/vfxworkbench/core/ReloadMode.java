package dev.vfxworkbench.core;

/** When a parameter change takes visual effect. */
public enum ReloadMode {
    /** Read every frame / tick; changing it updates the already-visible effect immediately. */
    HOT,
    /** Needs a shader/resource reload or mesh rebuild before it is applied. */
    RESOURCE,
    /** Needs a full registration change or client restart. */
    RESTART
}
