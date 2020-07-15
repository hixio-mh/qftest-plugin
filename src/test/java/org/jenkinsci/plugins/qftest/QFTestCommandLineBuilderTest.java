package org.jenkinsci.plugins.qftest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandLineTests {

    String binary = new String("qftest");
    QFTestCommandLine builder;

    @BeforeEach
    public void initBuilder() {
        builder = new QFTestCommandLine("qftest", QFTestCommandLine.RunMode.RUN);
    }

    @Test
    public void enforce() {
        builder.presetArg(ExtendedArgumentListBuilder.PresetType.ENFORCE, "-batch")
                .addTokenized("aSuite.qft");

        Assertions.assertEquals(binary + " -run -batch aSuite.qft",
                builder.toString(), "Enforce does not work as intended");
    }

    @Test
    public void overwrite() {
        builder.presetArg(ExtendedArgumentListBuilder.PresetType.ENFORCE, "-batch")
                .presetArg(ExtendedArgumentListBuilder.PresetType.OVERWRITE, "-logdir", "logHERE")
                .addTokenized("-logdir alogdir aSuite.qft");

        Assertions.assertEquals(binary + " -run -batch -logdir logHERE aSuite.qft",
                builder.toString(), "Overwrite does not work as intended");

        Assertions.assertEquals("-logdir alogdir",
                String.join(" ", builder.getAlteredArgs())
        );
    }

    @Test
    public void drop1() {
        builder.presetArg(ExtendedArgumentListBuilder.PresetType.ENFORCE, "-batch")
                .presetArg(ExtendedArgumentListBuilder.PresetType.DROP, "-dontDoThat")
                .addTokenized("-dontDoThat this aSuite.qft");

        Assertions.assertEquals(binary + " -run -batch this aSuite.qft",
                builder.toString(), "Drop does not work as intended");

        Assertions.assertEquals("-dontDoThat",
                String.join(" ", builder.getAlteredArgs())
        );
    }

    @Test
    public void drop2() {
       builder.presetArg(ExtendedArgumentListBuilder.PresetType.DROP, "-dontDoThat", "")
                .presetArg(ExtendedArgumentListBuilder.PresetType.ENFORCE, "-batch")
                .addTokenized("-dontDoThat this aSuite.qft");

        Assertions.assertEquals(binary + " -run -batch aSuite.qft",
                builder.toString(), "Drop does not work as intended");

        Assertions.assertEquals("-dontDoThat this",
                String.join(" ", builder.getAlteredArgs())
        );
    }

    @Test
    public void default1() {
        builder.presetArg(ExtendedArgumentListBuilder.PresetType.ENFORCE, "-batch")
                .presetArg(ExtendedArgumentListBuilder.PresetType.DEFAULT, "-logdir", "logHERE")
                .addTokenized("aSuite.qft");

        Assertions.assertEquals(binary + " -run -batch -logdir logHERE aSuite.qft",
                builder.toString(), "Default does not work as intended");
    }

    @Test
    public void default2() {
        builder.presetArg(ExtendedArgumentListBuilder.PresetType.ENFORCE, "-batch")
                .presetArg(ExtendedArgumentListBuilder.PresetType.DEFAULT, "-logdir", "logHERE")
                .addTokenized("-logdir logTHERE aSuite.qft");

        Assertions.assertEquals(binary + " -run -batch -logdir logTHERE aSuite.qft",
                builder.toString(), "Default does not work as intended");
    }
};

