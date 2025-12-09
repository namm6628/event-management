package com.example.myapplication.attendee.home;

import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

public class HeroVideoAdapter extends RecyclerView.Adapter<HeroVideoAdapter.VH> {

    public interface OnHeroClick {
        void onHeroClick(@NonNull Event e);
    }

    private final Context context;
    private final OnHeroClick onHeroClick;
    private final List<Event> items = new ArrayList<>();

    private final ExoPlayer player;
    private boolean isMuted = true;
    private int currentIndex = 0;

    private final SparseArray<VH> holderMap = new SparseArray<>();

    public HeroVideoAdapter(Context context, OnHeroClick click) {
        this.context = context;
        this.onHeroClick = click;

        player = new ExoPlayer.Builder(context).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setVolume(0f);
    }

    public void submitList(List<Event> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hero_video, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Event e = items.get(position);
        h.bind(e, this, onHeroClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }


    @Override
    public void onViewAttachedToWindow(@NonNull VH holder) {
        super.onViewAttachedToWindow(holder);
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            holderMap.put(pos, holder);
            // Nếu đây là slide hiện tại -> gắn player luôn
            if (pos == currentIndex) {
                attachPlayerTo(pos);
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull VH holder) {
        super.onViewDetachedFromWindow(holder);
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            holderMap.remove(pos);
        }
        holder.playerView.setPlayer(null);
    }

    private void attachPlayerTo(int position) {
        for (int i = 0; i < holderMap.size(); i++) {
            VH h = holderMap.valueAt(i);
            h.playerView.setPlayer(null);
        }
        VH target = holderMap.get(position);
        if (target != null) {
            target.playerView.setPlayer(player);
            target.btnMute.setImageResource(
                    isMuted ? R.drawable.ic_volume_off_white_24
                            : R.drawable.ic_volume_up_white_24
            );
        }
    }

    public void onPageSelected(int position) {
        if (items.isEmpty()) return;
        if (position < 0 || position >= items.size()) return;

        currentIndex = position;
        attachPlayerTo(position);
        playAt(position);
    }

    private void playAt(int position) {
        if (items.isEmpty()) return;
        if (position < 0 || position >= items.size()) return;

        currentIndex = position;
        Event e = items.get(position);
        String url = e.getVideoUrl();
        if (url == null || url.trim().isEmpty()) return;

        MediaItem mediaItem = MediaItem.fromUri(url);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void release() {
        player.release();
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void toggleMute() {
        isMuted = !isMuted;
        player.setVolume(isMuted ? 0f : 1f);
    }

    // ============= ViewHolder =============

    static class VH extends RecyclerView.ViewHolder {

        final PlayerView playerView;
        final ImageButton btnMute;

        VH(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.playerHeroItem);
            btnMute    = itemView.findViewById(R.id.btnMuteItem);
        }

        void bind(Event e, HeroVideoAdapter adapter, OnHeroClick click) {

            btnMute.setImageResource(
                    adapter.isMuted()
                            ? R.drawable.ic_volume_off_white_24
                            : R.drawable.ic_volume_up_white_24
            );

            btnMute.setOnClickListener(v -> {
                adapter.toggleMute();
                btnMute.setImageResource(
                        adapter.isMuted()
                                ? R.drawable.ic_volume_off_white_24
                                : R.drawable.ic_volume_up_white_24
                );
            });

            itemView.setOnClickListener(v -> {
                if (click != null) click.onHeroClick(e);
            });
        }
    }
}
