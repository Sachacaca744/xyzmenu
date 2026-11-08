package fr.uboards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Donnees d'un tableau / ecran : taille, texte, image. */
public class BoardBE extends BlockEntity {
    public static final int MAX_SIZE = 32;

    private int w = 1;
    private int h = 1;
    private String text = "";
    private String img = "";

    public BoardBE(BlockPos pos, BlockState state) {
        super(Reg.BE.get(), pos, state);
    }

    public static int clamp(int v) {
        return Math.max(1, Math.min(MAX_SIZE, v));
    }

    public int getW() { return w; }
    public int getH() { return h; }
    public String getText() { return text; }
    public String getImg() { return img; }

    public boolean isScreen() {
        return getBlockState().getBlock() instanceof BoardBlock b && b.isScreen();
    }

    public void setText(String t) {
        this.text = t == null ? "" : (t.length() > 900 ? t.substring(0, 900) : t);
        sync();
    }

    public void setImg(String hash) {
        this.img = hash == null ? "" : hash;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("W", w);
        tag.putInt("H", h);
        tag.putString("Text", text);
        tag.putString("Img", img);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("W")) w = clamp(tag.getInt("W"));
        if (tag.contains("H")) h = clamp(tag.getInt("H"));
        text = tag.getString("Text");
        img = tag.getString("Img");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Direction "droite" vue de face. */
    public Direction right() {
        return getBlockState().getValue(BoardBlock.FACING).getCounterClockWise();
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction r = right();
        AABB a = new AABB(worldPosition);
        AABB b = a.move(r.getStepX() * (w - 1), h - 1, r.getStepZ() * (w - 1));
        return a.minmax(b).inflate(0.3);
    }
}
