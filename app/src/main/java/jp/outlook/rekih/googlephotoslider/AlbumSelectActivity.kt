package jp.outlook.rekih.googlephotoslider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.outlook.rekih.googlephotoslider.databinding.ActivityAlbumSelectBinding
import jp.outlook.rekih.googlephotoslider.model.Album
import jp.outlook.rekih.googlephotoslider.viewmodel.AlbumSelect

class AlbumSelectActivity : AppCompatActivity() {

    private val albumSelect: AlbumSelect by viewModels()

    private lateinit var binding: ActivityAlbumSelectBinding

    private val albumListAdapter = AlbumListAdapter(object : AlbumListAdapter.ListListener {
        override fun onClickItem(tappedView: View, album: Album) {
            selectAlbum(album)
        }
    })

    private fun selectAlbum(album: Album) {
        val intent = Intent(application, SlideShowActivity::class.java)
        intent.putExtra(EXTRA_ALBUM_ID, album.id)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAlbumSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.albumList.adapter = albumListAdapter

        binding.albumList.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            itemAnimator = DefaultItemAnimator()
            adapter = albumListAdapter
        }

        albumSelect.albumList.observe(this, { albums ->
            Log.i("albumselect", "got albumlist:$albums")
            albumListAdapter.submitList(albums)
        })
        albumSelect.loading.observe(this, { loading ->
            binding.circularIndicator.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        })

        // スプラッシュ画像消去
        binding.splashImageview.setImageResource(0)

        albumSelect.loadAlbumList()
    }

    private class AlbumListAdapter(private val listener: ListListener) :
        RecyclerView.Adapter<AlbumListAdapter.AlbumItemViewHolder>() {
        private var list: List<Album> = listOf()

        class AlbumItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {}

        interface ListListener {
            fun onClickItem(tappedView: View, album: Album)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_album_select_item, parent, false)
            return AlbumItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: AlbumItemViewHolder, position: Int) {
            val item = list[position]
            holder.itemView.apply {
                findViewById<TextView>(R.id.name).text = item.title
                findViewById<TextView>(R.id.media_count).text = item.mediaItemsCount
                item.coverPhotoBitmap?.let {
                    findViewById<ImageView>(R.id.cover_image).setImageBitmap(it)
                }
                setOnClickListener {
                    listener.onClickItem(it, item)
                }
            }
        }

        override fun getItemCount(): Int = list.size

        fun submitList(newList: List<Album>) {
            list = newList
            notifyDataSetChanged()
        }
    }
}

