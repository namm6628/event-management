package com.example.myapplication.attendee.profile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.example.myapplication.R;
import com.example.myapplication.auth.AuthManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;

import java.io.File;

public class ProfileFragment extends Fragment {

    // --- Pick ảnh / chụp ảnh & xin quyền ---
    private ActivityResultLauncher<String> pickImage;          // chọn từ thư viện
    private ActivityResultLauncher<Uri> takePhoto;             // chụp ảnh mới
    private ActivityResultLauncher<String> requestReadPerm;    // READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE
    private ActivityResultLauncher<String> requestCameraPerm;  // CAMERA
    private Uri cameraUri;                                     // nơi lưu ảnh vừa chụp

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chọn ảnh từ thư viện
        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && getView() != null) {
                ImageView av = getView().findViewById(R.id.imgAvatar);
                av.setImageURI(uri);
                AuthManager.setAvatarUri(requireContext(), uri.toString());
            }
        });

        // Chụp ảnh mới
        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraUri != null && getView() != null) {
                ImageView av = getView().findViewById(R.id.imgAvatar);
                av.setImageURI(cameraUri);
                AuthManager.setAvatarUri(requireContext(), cameraUri.toString());
            }
        });

        // Xin quyền đọc ảnh
        requestReadPerm = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) pickImage.launch("image/*");
                    else toast("Cần quyền để chọn ảnh");
                }
        );

        // Xin quyền camera
        requestCameraPerm = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) openCamera();
                    else toast("Cần quyền camera để chụp ảnh");
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        ShapeableImageView av = v.findViewById(R.id.imgAvatar);
        if (av != null) {
            av.setShapeAppearanceModel(
                    av.getShapeAppearanceModel().toBuilder()
                            .setAllCornerSizes(new RelativeCornerSize(0.5f)) // 50% = tròn
                            .build()
            );
        }

        // Nút trên header
        View btnHeader = v.findViewById(R.id.btnEditProfile);
        if (btnHeader != null) {
            btnHeader.setOnClickListener(view ->
                    NavHostFragment.findNavController(this).navigate(R.id.loginFragment)
            );
        }

        // Nút login riêng (nếu có)
        View btnLogin = v.findViewById(R.id.btnLogin);
        if (btnLogin != null) {
            btnLogin.setOnClickListener(view ->
                    NavHostFragment.findNavController(this).navigate(R.id.loginFragment)
            );
        }

        // Bấm avatar -> menu 2 lựa chọn (chỉ khi đã đăng nhập)
        ImageView img = v.findViewById(R.id.imgAvatar);
        if (img != null) {
            img.setOnClickListener(view -> {
                if (AuthManager.isLoggedIn(requireContext())) {
                    showAvatarMenu(); // Xem / Chọn ảnh khác
                } else {
                    toast("Vui lòng đăng nhập để thao tác với ảnh đại diện");
                }
            });
        }

        updateUi(v); // lần đầu
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) updateUi(v);
    }

    /* -------------------------- UI logic -------------------------- */

    private void updateUi(@NonNull View root) {
        boolean logged = AuthManager.isLoggedIn(requireContext());
        String name   = AuthManager.getName(requireContext());

        TextView tvName  = root.findViewById(R.id.tvName);
        TextView tvEmail = root.findViewById(R.id.tvEmail);
        MaterialButton btn = root.findViewById(R.id.btnEditProfile);
        ImageView avatar = root.findViewById(R.id.imgAvatar);

        // Avatar: chỉ load ảnh đã lưu khi ĐÃ đăng nhập; chưa đăng nhập thì reset về mặc định
        if (avatar != null) {
            if (logged) {
                String savedUri = AuthManager.getAvatarUri(requireContext());
                if (savedUri != null) {
                    avatar.setImageURI(Uri.parse(savedUri));
                } else {
                    avatar.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else {
                avatar.setImageDrawable(null); // clear reference
                avatar.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        if (logged) {
            if (tvName != null)  tvName.setText(name);
            if (tvEmail != null) tvEmail.setText("Chào " + name + "!");
            if (btn != null) {
                btn.setText("Đăng xuất");
                btn.setOnClickListener(v -> showLogoutConfirm(root));
            }

            setupExpandable(root, R.id.rowProfile,        R.id.panelProfile,  R.id.chevronProfile);
            setupExpandable(root, R.id.rowPaymentMethods, R.id.panelPayment,  R.id.chevronPayment);
            setupExpandable(root, R.id.rowMyTickets,      R.id.panelTickets,  R.id.chevronTickets);

        } else {
            if (tvName != null)  tvName.setText("Khách mới");
            if (tvEmail != null) tvEmail.setText("Đăng nhập để sử dụng toàn bộ các dịch vụ bạn nhé!");
            if (btn != null) {
                btn.setText("Đăng nhập");
                btn.setOnClickListener(v ->
                        NavHostFragment.findNavController(this).navigate(R.id.loginFragment));
            }

            View.OnClickListener ask = v ->
                    toast("Vui lòng đăng nhập để xem mục này");
            int[] rows = { R.id.rowProfile, R.id.rowPaymentMethods, R.id.rowMyTickets };
            for (int id : rows) { View r = root.findViewById(id); if (r != null) r.setOnClickListener(ask); }
            int[] panels = { R.id.panelProfile, R.id.panelPayment, R.id.panelTickets };
            for (int id : panels) { View p = root.findViewById(id); if (p != null) p.setVisibility(View.GONE); }
            int[] chevs = { R.id.chevronProfile, R.id.chevronPayment, R.id.chevronTickets };
            for (int id : chevs) { ImageView c = root.findViewById(id); if (c != null) c.setRotation(0f); }
        }
    }

    private void showLogoutConfirm(@NonNull View root) {
        final int titleColor = ContextCompat.getColor(requireContext(), android.R.color.black);
        final int positiveColor = ContextCompat.getColor(requireContext(), R.color.md_primary);
        final int negativeColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray);

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(colorize("Đăng xuất?", titleColor))
                .setMessage("Bạn có chắc muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (d, w) -> {
                    // ✨ Reset avatar về mặc định + xoá URI đã lưu
                    try { AuthManager.setAvatarUri(requireContext(), null); } catch (Exception ignore) {}
                    AuthManager.logout(requireContext());
                    updateUi(root);
                })
                .setNegativeButton("Huỷ", null)
                .create();

        dlg.setOnShowListener(di -> tintDialogButtons(dlg, positiveColor, negativeColor));
        dlg.show();
    }

    private void setupExpandable(@NonNull View root,
                                 @IdRes int rowId,
                                 @IdRes int panelId,
                                 @IdRes int chevronId) {
        View row = root.findViewById(rowId);
        View panel = root.findViewById(panelId);
        ImageView chev = root.findViewById(chevronId);
        if (row == null || panel == null) return;

        row.setOnClickListener(v -> togglePanel(panel, chev));
    }

    private void togglePanel(@NonNull View panel, @Nullable ImageView chev) {
        ViewGroup parent = (ViewGroup) panel.getParent();
        Transition t = new AutoTransition();
        t.setDuration(180);
        TransitionManager.beginDelayedTransition(parent, t);

        boolean show = panel.getVisibility() != View.VISIBLE;
        panel.setVisibility(show ? View.VISIBLE : View.GONE);

        if (chev != null) {
            chev.animate().rotation(show ? 90f : 0f).setDuration(180).start();
        }
    }

    /* --------- Menu avatar & bottom sheet chọn nguồn ảnh --------- */

    private void showAvatarMenu() {
        final int titleColor = ContextCompat.getColor(requireContext(), android.R.color.black);

        CharSequence[] items = colorizeAll(
                new CharSequence[]{"Xem ảnh đại diện", "Chọn ảnh khác"},
                titleColor
        );

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(colorize("Ảnh đại diện", titleColor))
                .setItems(items, (d, which) -> {
                    if (which == 0) showAvatarPreview();
                    else if (which == 1) showPickSourceSheet(); // <— bottom sheet "xịn"
                })
                .create();
        dlg.show();
    }

    private void showPickSourceSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());

        // Tạo 2 hàng action thuần code (không cần XML)
        View root = new View(requireContext()); // placeholder để apply theme insets
        sheet.setContentView(buildPickSourceContent(sheet));
        sheet.show();
    }

    private View buildPickSourceContent(BottomSheetDialog sheet) {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        android.widget.LinearLayout ll = new android.widget.LinearLayout(requireContext());
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(pad, pad, pad, pad);

        ll.addView(makeActionRow(
                android.R.drawable.ic_menu_camera,
                "Chụp ảnh mới",
                v -> { sheet.dismiss(); ensureCameraThenOpen(); }
        ));

        ll.addView(divider());

        ll.addView(makeActionRow(
                android.R.drawable.ic_menu_gallery,
                "Chọn từ thư viện",
                v -> { sheet.dismiss(); ensureReadThenPick(); }
        ));

        return ll;
    }

    private View makeActionRow(int icon, String text, View.OnClickListener onClick) {
        int pad = dp(12);

        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(pad, pad, pad, pad);
        row.setClickable(true);
        row.setFocusable(true);

        // ripple theo theme: ?attr/selectableItemBackground
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        android.widget.ImageView iv = new android.widget.ImageView(requireContext());
        iv.setImageResource(icon);
        android.widget.LinearLayout.LayoutParams ip =
                new android.widget.LinearLayout.LayoutParams(dp(24), dp(24));
        ip.rightMargin = pad;
        row.addView(iv, ip);

        android.widget.TextView label = new android.widget.TextView(requireContext());
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
        );
        row.addView(label,
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(onClick);
        return row;
    }

    private int dp(int d) {
        return (int) (d * getResources().getDisplayMetrics().density);
    }

    private View divider() {
        View v = new View(requireContext());
        int h = (int) (1 * getResources().getDisplayMetrics().density);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        v.setBackgroundColor(0x33FFFFFF);
        return v;
    }

    private void ensureReadThenPick() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), perm)
                == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*");
        } else {
            requestReadPerm.launch(perm);
        }
    }

    private void ensureCameraThenOpen() {
        String perm = Manifest.permission.CAMERA;
        if (ContextCompat.checkSelfPermission(requireContext(), perm)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestCameraPerm.launch(perm);
        }
    }

    private void openCamera() {
        cameraUri = createImageUri();
        if (cameraUri != null) {
            takePhoto.launch(cameraUri);
        } else {
            toast("Không tạo được file ảnh");
        }
    }

    private Uri createImageUri() {
        File dir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (dir == null) return null;
        File file = new File(dir, "avatar_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".fileprovider",
                file
        );
    }

    private void showAvatarPreview() {
        final int titleColor = ContextCompat.getColor(requireContext(), android.R.color.black);

        ImageView img = new ImageView(requireContext());
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        img.setPadding(pad, pad, pad, pad);

        String saved = AuthManager.getAvatarUri(requireContext());
        if (saved != null) {
            img.setImageURI(Uri.parse(saved));
        } else {
            ImageView current = getView() != null ? getView().findViewById(R.id.imgAvatar) : null;
            if (current != null && current.getDrawable() != null) {
                img.setImageDrawable(current.getDrawable());
            } else {
                img.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(colorize("Ảnh đại diện", titleColor))
                .setView(img)
                .setPositiveButton("Đóng", null)
                .show();
    }

    // ---------------- helpers ----------------
    private void toast(String m) { Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show(); }

    private CharSequence colorize(CharSequence text, int color) {
        android.text.SpannableString s = new android.text.SpannableString(text);
        s.setSpan(new android.text.style.ForegroundColorSpan(color), 0, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private CharSequence[] colorizeAll(CharSequence[] texts, int color) {
        CharSequence[] out = new CharSequence[texts.length];
        for (int i = 0; i < texts.length; i++) out[i] = colorize(texts[i], color);
        return out;
    }

    private void tintDialogButtons(AlertDialog dlg, int positiveColor, int negativeColor) {
        android.widget.Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button neg = dlg.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (pos != null) pos.setTextColor(positiveColor);
        if (neg != null) neg.setTextColor(negativeColor);
    }
}
