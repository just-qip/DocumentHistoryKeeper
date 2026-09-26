package org.acme.enums;

/** Роль аккаунта в конкретном проекте. Порядок объявления = порядок возрастания прав. */
public enum ProjectRole {
    VIEWER, EDITOR, OWNER;

    /**
     * @param other требуемая роль
     * @return {@code true}, если текущая роль не ниже {@code other}
     */
    public boolean atLeast(ProjectRole other) {
        return this.ordinal() >= other.ordinal();
    }
}