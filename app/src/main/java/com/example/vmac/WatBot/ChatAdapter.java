package com.example.vmac.WatBot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import java.util.ArrayList;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private static final int SELF = 100;
    private static final int WATSON = 200;
    private final ArrayList<Message> messageArrayList;

    public ChatAdapter(ArrayList<Message> messageArrayList) {
        this.messageArrayList = messageArrayList != null ? messageArrayList : new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView;
        // view type is to identify where to render the chat message
        // left or right
        if (viewType == SELF) {
            // self message
            itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.chat_item_self, parent, false);
        } else {
            // WatBot message
            itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.chat_item_watson, parent, false);
        }
        return new ViewHolder(itemView);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageArrayList.get(position);
        return "1".equals(message.getId()) ? SELF : WATSON;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        Message message = messageArrayList.get(position);
        
        // Always ensure message TextView is available
        if (holder.message != null) {
            holder.message.setVisibility(View.VISIBLE);
            holder.message.setText(message.getMessage());
        }

        // Handle image if ImageView is available
        if (holder.image != null) {
            if (message.getType() == Message.Type.IMAGE) {
                holder.message.setVisibility(View.GONE);
                holder.image.setVisibility(View.VISIBLE);
                String imageUrl = message.getUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    RequestOptions options = new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.placeholder_image)
                            .error(R.drawable.error_image);
                    
                    Glide.with(holder.image.getContext())
                            .load(imageUrl)
                            .apply(options)
                            .into(holder.image);
                } else {
                    holder.image.setImageResource(R.drawable.error_image);
                }
            } else {
                holder.image.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageArrayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView message;
        final ImageView image;

        public ViewHolder(@NonNull View view) {
            super(view);
            message = view.findViewById(R.id.message);
            image = view.findViewById(R.id.image);
        }
    }
}