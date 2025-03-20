package com.judiraal.yammo.mods.minecraft;

import com.judiraal.yammo.Yammo;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.DataFixerUpper;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.neoforged.neoforge.common.util.Lazy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class LazyDataFixerBuilder {
    private final Supplier<DataFixerBuilder.Result> supplier;
    private volatile DataFixerBuilder.Result realDFU;

    public LazyDataFixerBuilder(Supplier<DataFixerBuilder.Result> supplier) {
        this.supplier = supplier;
    }

    public DataFixerBuilder.Result build() {
        DataFixerBuilder builder = new DataFixerBuilder(SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            @Override
            public Result build() {
                return new Result(new LazyDataFixer()) {
                    @Override
                    public CompletableFuture<?> optimize(Set<DSL.TypeReference> requiredTypes, Executor executor) {
                        return CompletableFuture.completedFuture(null);
                    }
                };
            }
        };
        return builder.build();
    }

    public static void forceDFU() {
        if (DataFixers.getDataFixer() instanceof LazyDataFixer fixer) {
            fixer.builder.replaceWithRealDFU(false);
        }
    }

    private class LazyDataFixer extends DataFixerUpper {
        private final LazyDataFixerBuilder builder;

        Lazy<Schema> schema = Lazy.of(BasicSchema::new);

        protected LazyDataFixer() {
            super(null, null, null);
            this.builder = LazyDataFixerBuilder.this;
        }

        @Override
        public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
            if (version < newVersion) {
                if (realDFU != null) return analyzeAndUpdate(type, input, version, newVersion);
                replaceWithRealDFU(true);
                if (realDFU != null) return analyzeAndUpdate(type, input, version, newVersion);
            }
            return input;
        }

        private <T> Dynamic<T> analyzeAndUpdate(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
            Dynamic<T> update = realDFU.fixer().update(type, input, version, newVersion);
            if (input != update)
                Yammo.LOGGER.trace("dfu.update {} from version {} to {}", type, version, newVersion);
            return update;
        }

        @Override
        public Schema getSchema(int key) {
            try {
                throw new RuntimeException("getSchema access");
            } catch (Exception e) {
                Yammo.LOGGER.debug("dfu.getSchema access in {}", e.getStackTrace()[1]);
            }
            return schema.get();
        }
    }

    private static class BasicSchema extends Schema {
        public BasicSchema() {
            super(39450, null);
        }

        @Override
        public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        }

        @Override
        public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
            return null;
        }

        @Override
        public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
            return null;
        }

        @Override
        protected Map<String, Type<?>> buildTypes() {
            return new HashMap<>();
        }
    }

    private synchronized void replaceWithRealDFU(boolean logStackTrace) {
        if (realDFU == null) {
            try {
                throw new RuntimeException("Deferred DFU initialization");
            } catch (Exception e) {
                Yammo.LOGGER.info("DFU required", e);
            }
            realDFU = supplier.get();
        }
    }
}
