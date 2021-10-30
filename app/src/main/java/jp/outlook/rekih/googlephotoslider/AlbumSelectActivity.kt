package jp.outlook.rekih.googlephotoslider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.outlook.rekih.googlephotoslider.databinding.ActivityAlbumSelectBinding
import jp.outlook.rekih.googlephotoslider.databinding.ViewAlbumSelectItemBinding

class AlbumSelectActivity : AppCompatActivity() {

    private val slideShow : SlideShow by viewModels()

    private lateinit var binding: ActivityAlbumSelectBinding

    private val albumListAdapter = AlbumListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlbumSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.albumList.adapter = albumListAdapter
        slideShow.selectAlbum.observe(this, selectAlbum)
        slideShow.selectAlbum.value = listOf("ABC","DEF")
    }

    private val selectAlbum = Observer<List<String>> {
        albums -> albumListAdapter.submitList(albums)
    }
}

class AlbumListAdapter : ListAdapter<String, AlbumItemViewHolder>(DIFF_UTIL_ITEM_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumItemViewHolder {
        val view = ViewAlbumSelectItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class AlbumItemViewHolder(
    private val binding: ViewAlbumSelectItemBinding
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(name: String) {
        binding.albumName.text = name
    }
}

val DIFF_UTIL_ITEM_CALLBACK = object : DiffUtil.ItemCallback<String>() {
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
        return oldItem == newItem
    }
}