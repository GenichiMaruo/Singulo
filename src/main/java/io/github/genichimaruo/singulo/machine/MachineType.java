package io.github.genichimaruo.singulo.machine;

/**
 * 汎用加工装置の種類。装置ごとの違いはスロット数・タンク数・質量モードの有無だけで、
 * 何を作れるかはレシピ（station がこの id のもの）で決まる。
 */
public enum MachineType {
    /** 入力タンクは純酸素転炉の酸素用。 */
    KILN("kiln", 1, 2, 1, 1, 0, false),
    COMPRESSOR("compressor", 1, 1, 1, 0, 1, true),
    ELECTROLYZER("electrolyzer", 1, 0, 0, 1, 2, false),
    ARCHIVE_TERMINAL("archive_terminal", 1, 4, 1, 0, 0, false),
    /** 段階3以降の装置・部品・道具を組み立てる。材料は種類と個数だけで指定する（入力6枠）。 */
    PRECISION_ASSEMBLER("precision_assembler", 2, 6, 1, 0, 0, false),
    /** 2つめの出力枠に、粗銅の精錬で出るビスマスなどの副産物が出る。 */
    CATALYTIC_REACTOR("catalytic_reactor", 2, 5, 2, 1, 0, false),
    /** マルチブロック。入力タンクはヘリウム用、出力タンクは液体窒素と液体ヘリウム。 */
    CRYOGENIC_COOLING_TOWER("cryogenic_cooling_tower", 2, 5, 1, 1, 2, false),
    /** マルチブロック。水素からミュオン束、入力の2枠の原子核どうし（亜鉛とビスマス）を衝突させてニホニウムを作る。
     *  2つめの出力枠に、ごく稀に磁気単極子が出る。 */
    PARTICLE_ACCELERATOR("particle_accelerator", 2, 2, 2, 1, 0, false),
    /** 量子もつれ素子を必ず2個1組で作る。 */
    ENTANGLEMENT_SYNTHESIZER("entanglement_synthesizer", 3, 5, 1, 1, 0, false),
    /** 入力タンクは液体窒素と液体ヘリウム。冷却原子トラップの復元もここで行う。 */
    LASER_COOLER("laser_cooler", 3, 4, 1, 2, 0, false),
    /** 型の残響の欠片（1個だけ入れる）の振動をスカルクに転写して、欠片を複製する。 */
    ECHO_RESONATOR("echo_resonator", 3, 4, 1, 0, 0, false),
    /** マルチブロック（3×3×3）。縮退物質殻・炉殻ブロック・ホライズン・バスを作る。圧縮熱を液体窒素で除く。 */
    DEGENERATE_COMPACTOR("degenerate_compactor", 4, 3, 1, 1, 0, false),
    /** マルチブロック（5×5×5）。アクシオン凝縮体（出力タンク）とエキゾチック物質を作る。 */
    CASIMIR_CAVITY("casimir_cavity", 4, 6, 1, 1, 1, false),
    /** 時間結晶触媒を育てる。電力が要求の80%を割った時間の割合だけ純度（寿命）が下がる。 */
    TIME_CRYSTAL_INCUBATOR("time_crystal_incubator", 4, 5, 1, 0, 0, false),
    /** 人工星核・特異点の種・シンギュラリティ・コアを作り、アノマリー・サンプルを復元する。 */
    SINGULARITY_ENCAPSULATOR("singularity_encapsulator", 5, 6, 1, 0, 0, false),
    /** 熱電素子に電流を流して熱を運び出し、タンクの水を凍らせる（熱電発電機の逆）。 */
    PELTIER_COOLER("peltier_cooler", 1, 0, 1, 1, 0, false);

    public static final int ENERGY_CAPACITY = 20_000;
    public static final int TANK_CAPACITY = 8_000;

    private final String id;
    private final int stage;
    private final int inputSlots;
    private final int outputSlots;
    private final int fluidInputs;
    private final int fluidOutputs;
    private final boolean massMode;

    MachineType(String id, int stage, int inputSlots, int outputSlots, int fluidInputs, int fluidOutputs, boolean massMode) {
        this.id = id;
        this.stage = stage;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.massMode = massMode;
    }

    public String id() {
        return id;
    }

    public int stage() {
        return stage;
    }

    public int inputSlots() {
        return inputSlots;
    }

    public int outputSlots() {
        return outputSlots;
    }

    public boolean hasOutput() {
        return outputSlots > 0;
    }

    /** 最初の出力スロットの番号（入力の後ろ）。 */
    public int outputSlot() {
        return inputSlots;
    }

    /** 触媒を使う装置は、触媒だけが入る専用スロットを持つ（ほかの入力スロットには触媒が入らない）。 */
    public boolean hasCatalystSlot() {
        return CATALYST_TYPES.contains(this);
    }

    /** 触媒スロットの番号（出力の後ろ）。なければ -1。 */
    public int catalystSlot() {
        return hasCatalystSlot() ? inputSlots + outputSlots : -1;
    }

    /** 単極子アップグレードのスロットの番号（いちばん後ろ）。 */
    public int upgradeSlot() {
        return inputSlots + outputSlots + (hasCatalystSlot() ? 1 : 0);
    }

    public int itemSlots() {
        return upgradeSlot() + 1;
    }

    /** レシピの照合に使うスロットの数（入力 + 触媒）。 */
    public int recipeSlots() {
        return inputSlots + (hasCatalystSlot() ? 1 : 0);
    }

    /** 照合の並び（入力 → 触媒）の i 番目のスロットの番号。 */
    public int recipeSlot(int i) {
        return i < inputSlots ? i : catalystSlot();
    }

    public int fluidInputs() {
        return fluidInputs;
    }

    public int fluidOutputs() {
        return fluidOutputs;
    }

    public int tanks() {
        return fluidInputs + fluidOutputs;
    }

    private static final java.util.Set<MachineType> CATALYST_TYPES = java.util.EnumSet.of(PRECISION_ASSEMBLER, LASER_COOLER,
            CASIMIR_CAVITY, TIME_CRYSTAL_INCUBATOR, SINGULARITY_ENCAPSULATOR);

    /**
     * 作るものを画面で指定できるか（{@link RecipeSelection}）。材料を種類と個数だけで指定するため、あるレシピの材料を
     * 入れている途中が、別のレシピの材料と同じになることがある装置。
     */
    public boolean selectsRecipe() {
        return this == PRECISION_ASSEMBLER || this == SINGULARITY_ENCAPSULATOR;
    }

    /** 任意のブロックを質量として溜め、圧縮ブロックや質量ペレットにするモードを持つか。 */
    public boolean massMode() {
        return massMode;
    }

    /**
     * 0.10.0 より前のスロットの並び（入力・出力の数）。スロットの数が変わった装置だけ。並びを書いていない古いデータは、
     * これで読み替える（{@link MachineBlockEntity} の読み込み）。
     */
    public int[] legacyLayout() {
        return switch (this) {
            case PARTICLE_ACCELERATOR -> new int[]{0, 2};
            case CATALYTIC_REACTOR -> new int[]{5, 1};
            default -> new int[]{inputSlots, outputSlots};
        };
    }

    /** マルチブロック（コントローラで動く）か。 */
    public boolean isMultiblock() {
        return this == CRYOGENIC_COOLING_TOWER || this == PARTICLE_ACCELERATOR || this == DEGENERATE_COMPACTOR
                || this == CASIMIR_CAVITY;
    }

    /** 電力の容量。段階が上がるほど消費が大きいので大きくする（受け入れは毎tick 容量の1/10まで）。 */
    public int energyCapacity() {
        return switch (stage) {
            case 1 -> ENERGY_CAPACITY;
            case 2 -> 100_000;
            case 3 -> 400_000;
            case 4 -> 4_000_000;
            default -> 40_000_000;
        };
    }
}
