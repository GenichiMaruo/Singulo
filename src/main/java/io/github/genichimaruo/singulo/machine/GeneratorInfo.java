package io.github.genichimaruo.singulo.machine;

/**
 * 発電機の状態表示（ThermoelectricGeneratorMenu）を使うブロックエンティティ。
 * 値の並びは ThermoelectricGeneratorBlockEntity の D_* に合わせる。
 */
public interface GeneratorInfo {
    SyncedInts syncData();
}
