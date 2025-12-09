package com.example.myapplication.attendee.profile;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.admin.AdminDashboardActivity;
import com.example.myapplication.attendee.detail.EventDetailActivity;
import com.example.myapplication.auth.AuthManager;
import com.example.myapplication.organizer.checkin.StaffCheckinEventListActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final long TIER_MEMBER_MIN = 1_000;
    private static final long TIER_VIP_MIN = 10_000;
    private static final long TIER_ELITE_MIN = 100_000;

    private ActivityResultLauncher<String> pickImage;
    private ActivityResultLauncher<Uri> takePhoto;
    private ActivityResultLauncher<String> requestReadPerm;
    private ActivityResultLauncher<String> requestCameraPerm;
    private Uri cameraUri;

    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchNotifications;

    private MaterialButton btnStaffCheckin;
    private MaterialButton btnRequestMember;
    private MaterialButton btnUpgradeVip;
    private MaterialButton btnAdminPanel;

    private AuthManager authManager;
    private View btnGoOrg;
    private View btnRequestOrg;

    // Loyalty UI
    private TextView tvMembershipBadge;
    private TextView tvLoyaltyPoints;
    private TextView tvTierLabel;
    private TextView tvNextTierHint;
    private ProgressBar progressNextTier;
    private TextView tvLifetimePoints;

    private FirebaseFirestore db;
    private View containerFavoriteEventsView;
    private View btnToggleFavoriteEventsView;
    private TextView tvEmptyFavoriteEvents;
    private boolean favoritesExpanded = false;
    private final List<FavoriteEvent> favoriteEvents = new ArrayList<>();
    private static final int FAVORITES_COLLAPSED_LIMIT = 2;

    private TextInputEditText edtProfileName;
    private TextInputEditText edtProfileEmail;
    private TextInputEditText edtProfilePhone;
    private MaterialButton btnEditInfo;
    private boolean isEditingInfo = false;

    private TextView tvPrimaryCard;

    private static class FavoriteEvent {
        String id;
        String title;
        String location;

        FavoriteEvent(String id, String title, String location) {
            this.id = id;
            this.title = title;
            this.location = location;
        }
    }

    private static class NextTierInfo {
        String nextTierLabel;
        long remainingPoints;
        int progressPercent;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();

        pickImage = registerForActivityResult(new ActivityResultContracts.GetContent(), src -> {
            if (!isAdded()) return;
            View root = getView();
            if (src != null && root != null) {
                Uri local = copyToAppStorage(src);
                if (local == null) local = src;

                ImageView av = root.findViewById(R.id.imgAvatar);
                if (local != null) {
                    av.setImageURI(local);
                    Context ctx = getContext();
                    if (ctx != null) AuthManager.setAvatarUri(ctx, local.toString());
                    uploadAvatarToCloud(local);
                } else {
                    toast("Không lưu được ảnh đã chọn");
                }
            }
        });


        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (!isAdded()) return;
            View root = getView();
            if (success && cameraUri != null && root != null) {
                ImageView av = root.findViewById(R.id.imgAvatar);
                av.setImageURI(cameraUri);
                Context ctx = getContext();
                if (ctx != null) AuthManager.setAvatarUri(ctx, cameraUri.toString());
                uploadAvatarToCloud(cameraUri);
            }
        });

        requestReadPerm = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isAdded()) return;
                    if (granted) pickImage.launch("image/*");
                    else toast("Cần quyền để chọn ảnh");
                }
        );

        requestCameraPerm = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isAdded()) return;
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

        authManager = new AuthManager();
        btnGoOrg = v.findViewById(R.id.btnGoOrganizer);
        btnRequestOrg = v.findViewById(R.id.btnRequestOrganizer);
        btnStaffCheckin = v.findViewById(R.id.btnStaffCheckin);
        btnAdminPanel = v.findViewById(R.id.btnAdminPanel);
        btnRequestMember = v.findViewById(R.id.btnRequestMember);
        btnUpgradeVip = v.findViewById(R.id.btnUpgradeVip);

        switchDarkMode = v.findViewById(R.id.switchDarkMode);
        switchNotifications = v.findViewById(R.id.switchNotifications);

        tvMembershipBadge = v.findViewById(R.id.tvMembershipBadge);
        tvLoyaltyPoints = v.findViewById(R.id.tvLoyaltyPoints);
        tvLifetimePoints = v.findViewById(R.id.tvLifetimePoints);
        tvTierLabel = v.findViewById(R.id.tvTierLabel);
        tvNextTierHint = v.findViewById(R.id.tvNextTierHint);
        progressNextTier = v.findViewById(R.id.progressNextTier);

        edtProfileName = v.findViewById(R.id.edtName);
        edtProfileEmail = v.findViewById(R.id.edtEmail);
        edtProfilePhone = v.findViewById(R.id.edtPhone);
        btnEditInfo = v.findViewById(R.id.btnEditInfo);

        if (btnEditInfo != null) {
            btnEditInfo.setOnClickListener(view -> onEditInfoClick());
        }

        tvPrimaryCard = v.findViewById(R.id.tvPrimaryCard);

        if (tvPrimaryCard != null) {
            tvPrimaryCard.setText("Chưa có thẻ nào được lưu");
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && tvPrimaryCard != null) {
            String uid = user.getUid();
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded()) return;
                        if (doc != null && doc.exists()) {
                            String cardMask = doc.getString("primaryCardMask");
                            if (cardMask != null && !cardMask.isEmpty()) {
                                tvPrimaryCard.setText("• " + cardMask);
                            } else {
                                tvPrimaryCard.setText("Chưa có thẻ nào được lưu");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        toast("Không tải được thẻ thanh toán");
                    });
        }

        View btnAddCard = v.findViewById(R.id.btnAddCard);

        if (btnGoOrg != null) {
            btnGoOrg.setOnClickListener(x ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.organizerHomeFragment)
            );
        }

        if (btnRequestOrg != null) {
            btnRequestOrg.setOnClickListener(x ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.organizerRequestFragment)
            );
        }

        setupStaffCheckinButton();
        setupAdminButton();

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        boolean currentlyDark =
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;

        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener(null);
            switchDarkMode.setChecked(currentlyDark);

            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("dark_mode", isChecked).apply();

                boolean darkNow =
                        (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                                == Configuration.UI_MODE_NIGHT_YES;
                if (isChecked != darkNow) {
                    AppCompatDelegate.setDefaultNightMode(
                            isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                    );
                }
            });
        }


        if (switchNotifications != null) {
            boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
            switchNotifications.setOnCheckedChangeListener(null);
            switchNotifications.setChecked(notificationsEnabled);

            switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
                if (isChecked) {
                    toast("Đã bật thông báo sự kiện");
                    // TODO: nếu dùng FCM -> subscribe topic ở đây
                } else {
                    toast("Đã tắt thông báo sự kiện. Bạn sẽ không nhận thông báo mới.");
                    // TODO: nếu dùng FCM -> unsubscribe topic ở đây
                }
            });
        }

        if (tvPrimaryCard != null) {
            String cardMask = prefs.getString("primary_card_mask", null);
            if (cardMask == null || cardMask.isEmpty()) {
                tvPrimaryCard.setText("Chưa có thẻ nào được lưu");
            } else {
                tvPrimaryCard.setText("• " + cardMask);
            }
        }

        if (btnAddCard != null && tvPrimaryCard != null) {
            btnAddCard.setOnClickListener(view -> showAddCardDialog(tvPrimaryCard));
        }

        ShapeableImageView av = v.findViewById(R.id.imgAvatar);
        if (av != null) {
            av.setShapeAppearanceModel(
                    av.getShapeAppearanceModel().toBuilder()
                            .setAllCornerSizes(new RelativeCornerSize(0.5f))
                            .build()
            );
        }

        View btnHeader = v.findViewById(R.id.btnEditProfile);

        View btnLogin = v.findViewById(R.id.btnLogin);
        if (btnLogin != null) {
            btnLogin.setOnClickListener(view ->
                    NavHostFragment.findNavController(this).navigate(R.id.loginFragment)
            );
        }

        ImageView img = v.findViewById(R.id.imgAvatar);
        if (img != null) {
            img.setOnClickListener(view -> {
                if (AuthManager.isLoggedIn(requireContext())) {
                    showAvatarMenu();
                } else {
                    toast("Vui lòng đăng nhập để thao tác với ảnh đại diện");
                }
            });
        }

        containerFavoriteEventsView = v.findViewById(R.id.containerFavoriteEvents);
        btnToggleFavoriteEventsView = v.findViewById(R.id.btnToggleFavoriteEvents);
        tvEmptyFavoriteEvents = v.findViewById(R.id.tvEmptyFavoriteEvents);

        if (btnToggleFavoriteEventsView != null) {
            btnToggleFavoriteEventsView.setOnClickListener(view -> {
                favoritesExpanded = !favoritesExpanded;
                if (btnToggleFavoriteEventsView instanceof TextView) {
                    ((TextView) btnToggleFavoriteEventsView)
                            .setText(favoritesExpanded ? "Ẩn bớt" : "Xem thêm");
                }
                renderFavoriteEventsUi();
            });
        }

        updateUi(v);
        loadBasicUserInfoIntoPanel(v);
        refreshFavoriteEvents(v);
        loadUserProfileAndLoyalty();
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) {
            updateUi(v);
            loadBasicUserInfoIntoPanel(v);
            refreshFavoriteEvents(v);
            loadUserProfileAndLoyalty();
        }

        if (btnGoOrg != null) {
            btnGoOrg.setVisibility(View.GONE);
        }
        if (btnRequestOrg != null) {
            btnRequestOrg.setVisibility(View.GONE);
        }

        authManager.refreshOrganizerStatus(requireContext(), isOrganizer -> {
            if (!isAdded()) return;

            if (btnGoOrg != null) {
                btnGoOrg.setVisibility(isOrganizer ? View.VISIBLE : View.GONE);
            }
            if (btnRequestOrg != null) {
                btnRequestOrg.setVisibility(isOrganizer ? View.GONE : View.VISIBLE);
            }
        });

        setupStaffCheckinButton();
        setupAdminButton();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        containerFavoriteEventsView = null;
        btnToggleFavoriteEventsView = null;
        tvEmptyFavoriteEvents = null;

        edtProfileName = null;
        edtProfileEmail = null;
        edtProfilePhone = null;
        btnEditInfo = null;
        tvPrimaryCard = null;
        switchNotifications = null;
        switchDarkMode = null;
    }


    private void setupStaffCheckinButton() {
        if (btnStaffCheckin == null) return;

        btnStaffCheckin.setVisibility(View.GONE);
        btnStaffCheckin.setOnClickListener(null);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            return;
        }

        String email = user.getEmail();

        db.collectionGroup("collaborators")
                .whereEqualTo("email", email)
                .whereEqualTo("role", "checkin")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    if (snap != null && !snap.isEmpty()) {
                        btnStaffCheckin.setVisibility(View.VISIBLE);
                        btnStaffCheckin.setOnClickListener(v -> {
                            Context ctx = requireContext();
                            Intent i = new Intent(ctx, StaffCheckinEventListActivity.class);
                            ctx.startActivity(i);
                        });
                    } else {
                        btnStaffCheckin.setVisibility(View.GONE);
                        btnStaffCheckin.setOnClickListener(null);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnStaffCheckin.setVisibility(View.GONE);
                    btnStaffCheckin.setOnClickListener(null);
                });
    }

    private void setupAdminButton() {
        if (btnAdminPanel == null) return;

        btnAdminPanel.setVisibility(View.GONE);
        btnAdminPanel.setOnClickListener(null);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (doc != null && doc.exists()) {
                        Boolean isAdmin = doc.getBoolean("isAdmin");
                        if (Boolean.TRUE.equals(isAdmin)) {
                            btnAdminPanel.setVisibility(View.VISIBLE);
                            btnAdminPanel.setOnClickListener(v -> {
                                Intent i = new Intent(requireContext(), AdminDashboardActivity.class);
                                startActivity(i);
                            });
                        } else {
                            btnAdminPanel.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnAdminPanel.setVisibility(View.GONE);
                });
    }


    private String getLoyaltyRankLabel(long lifetimePoints) {
        if (lifetimePoints >= TIER_ELITE_MIN) return "Elite";
        if (lifetimePoints >= TIER_VIP_MIN)   return "VIP";
        if (lifetimePoints >= TIER_MEMBER_MIN) return "Member";
        return "Thường";
    }

    private NextTierInfo computeNextTier(long lifetimePoints) {
        NextTierInfo info = new NextTierInfo();

        long currentMin;
        long nextMin;
        String nextLabel;

        if (lifetimePoints < TIER_MEMBER_MIN) {
            currentMin = 0;
            nextMin = TIER_MEMBER_MIN;
            nextLabel = "Member";
        } else if (lifetimePoints < TIER_VIP_MIN) {
            currentMin = TIER_MEMBER_MIN;
            nextMin = TIER_VIP_MIN;
            nextLabel = "VIP";
        } else if (lifetimePoints < TIER_ELITE_MIN) {
            currentMin = TIER_VIP_MIN;
            nextMin = TIER_ELITE_MIN;
            nextLabel = "Elite";
        } else {
            info.nextTierLabel = null;
            info.remainingPoints = 0;
            info.progressPercent = 100;
            return info;
        }

        long remaining = nextMin - lifetimePoints;
        if (remaining < 0) remaining = 0;

        long range = nextMin - currentMin;
        long progressed = lifetimePoints - currentMin;
        if (progressed < 0) progressed = 0;

        int percent = range == 0 ? 100 : (int) (progressed * 100 / range);
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;

        info.nextTierLabel = nextLabel;
        info.remainingPoints = remaining;
        info.progressPercent = percent;
        return info;
    }

    private void loadUserProfileAndLoyalty() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (tvLoyaltyPoints != null) tvLoyaltyPoints.setText("0");
            if (tvLifetimePoints != null) tvLifetimePoints.setText("0");
            if (tvTierLabel != null) tvTierLabel.setText("Hạng: Thường");
            if (tvNextTierHint != null) tvNextTierHint.setText("Đăng nhập để tích điểm và nhận ưu đãi.");
            if (progressNextTier != null) progressNextTier.setProgress(0);
            if (tvMembershipBadge != null) tvMembershipBadge.setVisibility(View.GONE);
            if (btnRequestMember != null) btnRequestMember.setVisibility(View.GONE);
            if (btnUpgradeVip != null) btnUpgradeVip.setVisibility(View.GONE);
            return;
        }

        String uid = user.getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (doc == null || !doc.exists()) return;

                    Long loyalty = doc.getLong("loyaltyPoints");
                    Long lifetime = doc.getLong("lifetimePoints");
                    String membershipTier = doc.getString("membershipTier");

                    if (loyalty == null) loyalty = 0L;
                    if (lifetime == null) lifetime = 0L;
                    if (membershipTier == null) membershipTier = "none";

                    NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
                    if (tvLoyaltyPoints != null) {
                        tvLoyaltyPoints.setText(nf.format(loyalty));
                    }
                    if (tvLifetimePoints != null) {
                        tvLifetimePoints.setText(nf.format(lifetime));
                    }

                    String loyaltyRankLabel = getLoyaltyRankLabel(lifetime);
                    if (tvTierLabel != null) {
                        tvTierLabel.setText("Hạng: " + loyaltyRankLabel);
                    }

                    if (tvMembershipBadge != null) {
                        if ("member".equalsIgnoreCase(membershipTier)
                                || "vip".equalsIgnoreCase(membershipTier)
                                || "elite".equalsIgnoreCase(membershipTier)) {

                            tvMembershipBadge.setVisibility(View.VISIBLE);

                            if ("vip".equalsIgnoreCase(membershipTier)
                                    || "elite".equalsIgnoreCase(membershipTier)) {
                                tvMembershipBadge.setText("VIP");
                                tvMembershipBadge.setBackground(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_vip_badge)
                                );
                            } else {
                                tvMembershipBadge.setText("MEMBER");
                                tvMembershipBadge.setBackground(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_member_badge)
                                );
                            }
                        } else {
                            tvMembershipBadge.setVisibility(View.GONE);
                        }
                    }

                    NextTierInfo info = computeNextTier(lifetime);

                    if (info.nextTierLabel == null) {
                        if (tvNextTierHint != null) {
                            tvNextTierHint.setText("Bạn đang ở hạng cao nhất. Cảm ơn đã ủng hộ!");
                        }
                        if (progressNextTier != null) {
                            progressNextTier.setProgress(100);
                        }
                    } else {
                        if (tvNextTierHint != null) {
                            tvNextTierHint.setText(
                                    "Còn " + info.remainingPoints + " điểm nữa để lên " + info.nextTierLabel
                            );
                        }
                        if (progressNextTier != null) {
                            progressNextTier.setProgress(info.progressPercent);
                        }
                    }

                    setupMemberButtonsWithTier(uid, membershipTier.toLowerCase(Locale.ROOT));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                });
    }

    private void setupMemberButtonsWithTier(String uid, String tier) {
        if (btnRequestMember == null || btnUpgradeVip == null) return;

        boolean isMember = "member".equalsIgnoreCase(tier);
        boolean isVipOrElite = "vip".equalsIgnoreCase(tier) || "elite".equalsIgnoreCase(tier);

        if (!isMember && !isVipOrElite) {
            btnRequestMember.setVisibility(View.VISIBLE);
            btnRequestMember.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Đăng ký thành viên?")
                        .setMessage("Bạn muốn gửi yêu cầu đăng ký làm thành viên không?")
                        .setPositiveButton("Có", (dialog, which) -> {
                            sendMemberRequest(uid, "member");
                        })
                        .setNegativeButton("Không", null)
                        .show();
            });
        } else {
            btnRequestMember.setVisibility(View.GONE);
        }

        if (isMember && !isVipOrElite) {
            btnUpgradeVip.setVisibility(View.VISIBLE);
            btnUpgradeVip.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Nâng cấp VIP?")
                        .setMessage("Bạn muốn gửi yêu cầu nâng cấp lên hạng VIP không?")
                        .setPositiveButton("Có", (dialog, which) -> {
                            sendMemberRequest(uid, "vip");
                        })
                        .setNegativeButton("Không", null)
                        .show();
            });
        } else {
            btnUpgradeVip.setVisibility(View.GONE);
        }
    }

    private void sendMemberRequest(String uid, String desiredTier) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("desiredTier", desiredTier);
        data.put("status", "pending");
        data.put("createdAt", Timestamp.now());

        db.collection("memberRequests")
                .document(uid)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    String msg = "member".equals(desiredTier)
                            ? "Đã gửi yêu cầu làm thành viên!"
                            : "Đã gửi yêu cầu nâng cấp VIP!";
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Gửi yêu cầu thất bại: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void updateUi(@NonNull View root) {
        boolean logged = AuthManager.isLoggedIn(requireContext());
        String name   = AuthManager.getName(requireContext());

        TextView tvName  = root.findViewById(R.id.tvName);
        TextView tvEmail = root.findViewById(R.id.tvEmail);
        MaterialButton btn = root.findViewById(R.id.btnEditProfile);
        ImageView avatar = root.findViewById(R.id.imgAvatar);

        if (avatar != null) {
            if (logged) {
                loadAvatarFromCloud(avatar);
            } else {
                setDefaultAvatar(avatar);
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

            if (btnToggleFavoriteEventsView != null) {
                btnToggleFavoriteEventsView.setVisibility(View.VISIBLE);
            }

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
            int[] rows = { R.id.rowProfile, R.id.rowPaymentMethods };
            for (int id : rows) {
                View r = root.findViewById(id);
                if (r != null) r.setOnClickListener(ask);
            }
            int[] panels = { R.id.panelProfile, R.id.panelPayment };
            for (int id : panels) {
                View p = root.findViewById(id);
                if (p != null) p.setVisibility(View.GONE);
            }
            int[] chevs = { R.id.chevronProfile, R.id.chevronPayment };
            for (int id : chevs) {
                ImageView c = root.findViewById(id);
                if (c != null) c.setRotation(0f);
            }

            favoriteEvents.clear();
            favoritesExpanded = false;
            if (btnToggleFavoriteEventsView instanceof TextView) {
                ((TextView) btnToggleFavoriteEventsView).setText("Xem thêm");
            }
            if (tvEmptyFavoriteEvents != null) {
                tvEmptyFavoriteEvents.setText("Đăng nhập để xem sự kiện theo dõi.");
                tvEmptyFavoriteEvents.setVisibility(View.VISIBLE);
            }
            if (btnToggleFavoriteEventsView != null) {
                btnToggleFavoriteEventsView.setVisibility(View.GONE);
            }
            renderFavoriteEventsUi();

            if (tvLoyaltyPoints != null) tvLoyaltyPoints.setText("0");
            if (tvLifetimePoints != null) tvLifetimePoints.setText("0");
            if (tvTierLabel != null) tvTierLabel.setText("Hạng: Thường");
            if (tvNextTierHint != null) tvNextTierHint.setText("Đăng nhập để tích điểm và nhận ưu đãi.");
            if (progressNextTier != null) progressNextTier.setProgress(0);
            if (tvMembershipBadge != null) tvMembershipBadge.setVisibility(View.GONE);
            if (btnRequestMember != null) btnRequestMember.setVisibility(View.GONE);
            if (btnUpgradeVip != null) btnUpgradeVip.setVisibility(View.GONE);
        }
    }

    private void setDefaultAvatar(@NonNull ImageView avatar) {
        avatar.setImageDrawable(null);
        avatar.setImageResource(android.R.drawable.sym_def_app_icon);
    }

    private void loadAvatarFromCloud(@NonNull ImageView avatar) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setDefaultAvatar(avatar);
            return;
        }
        String uid = user.getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    String url = doc != null ? doc.getString("avatarUrl") : null;
                    if (url != null && !url.isEmpty()) {
                        Glide.with(this)
                                .load(url)
                                .circleCrop()
                                .placeholder(android.R.drawable.sym_def_app_icon)
                                .error(android.R.drawable.sym_def_app_icon)
                                .into(avatar);
                    } else {
                        setDefaultAvatar(avatar);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setDefaultAvatar(avatar);
                });
    }

    private void uploadAvatarToCloud(@NonNull Uri fileUri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            toast("Cần đăng nhập để cập nhật ảnh đại diện");
            return;
        }

        String uid = user.getUid();
        String path = "user_avatars/" + uid + "/avatar_" + System.currentTimeMillis() + ".jpg";

        StorageReference ref = FirebaseStorage.getInstance()
                .getReference()
                .child(path);

        ref.putFile(fileUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    String url = downloadUri.toString();
                    db.collection("users")
                            .document(uid)
                            .update("avatarUrl", url)
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) return;
                                View root = getView();
                                if (root != null) {
                                    ImageView avatar = root.findViewById(R.id.imgAvatar);
                                    if (avatar != null) {
                                        Glide.with(this)
                                                .load(url)
                                                .circleCrop()
                                                .placeholder(android.R.drawable.sym_def_app_icon)
                                                .error(android.R.drawable.sym_def_app_icon)
                                                .into(avatar);
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                toast("Lưu avatarUrl thất bại");
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    toast("Upload ảnh đại diện thất bại");
                });
    }

    private void showLogoutConfirm(@NonNull View root) {
        final int titleColor = MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnSurface, 0);

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(colorize("Đăng xuất?", titleColor))
                .setMessage("Bạn có chắc muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();

                    try { AuthManager.setAvatarUri(requireContext(), null); } catch (Exception ignore) {}
                    AuthManager.logout(requireContext());

                    updateUi(root);

                    NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                })
                .setNegativeButton("Huỷ", null)
                .create();

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

    private void refreshFavoriteEvents(@NonNull View root) {
        if (!AuthManager.isLoggedIn(requireContext())) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        db.collection("users")
                .document(uid)
                .collection("favoriteEvents")
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || getView() == null) return;

                    favoriteEvents.clear();

                    if (snap.isEmpty()) {
                        if (tvEmptyFavoriteEvents != null) {
                            tvEmptyFavoriteEvents.setText("Bạn chưa theo dõi sự kiện nào.");
                            tvEmptyFavoriteEvents.setVisibility(View.VISIBLE);
                        }
                        if (btnToggleFavoriteEventsView != null) {
                            btnToggleFavoriteEventsView.setVisibility(View.GONE);
                        }
                        renderFavoriteEventsUi();
                        return;
                    }

                    if (tvEmptyFavoriteEvents != null) {
                        tvEmptyFavoriteEvents.setVisibility(View.GONE);
                    }

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String eventId = d.getId();
                        String title = d.getString("title");
                        String location = d.getString("location");

                        if (title == null) title = "(Không có tên)";
                        if (location == null) location = "";

                        favoriteEvents.add(new FavoriteEvent(eventId, title, location));
                    }

                    if (btnToggleFavoriteEventsView != null) {
                        btnToggleFavoriteEventsView.setVisibility(
                                favoriteEvents.size() > FAVORITES_COLLAPSED_LIMIT
                                        ? View.VISIBLE
                                        : View.GONE
                        );
                    }

                    renderFavoriteEventsUi();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getView() == null) return;
                    toast("Không tải được danh sách sự kiện yêu thích");
                    favoriteEvents.clear();
                    renderFavoriteEventsUi();
                });
    }

    private void renderFavoriteEventsUi() {
        if (!isAdded()) return;
        if (containerFavoriteEventsView == null) return;
        if (!(containerFavoriteEventsView instanceof ViewGroup)) return;

        ViewGroup container = (ViewGroup) containerFavoriteEventsView;
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        if (favoriteEvents.isEmpty()) {
            if (tvEmptyFavoriteEvents != null) {
                tvEmptyFavoriteEvents.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (tvEmptyFavoriteEvents != null) {
            tvEmptyFavoriteEvents.setVisibility(View.GONE);
        }

        int max = favoritesExpanded
                ? favoriteEvents.size()
                : Math.min(FAVORITES_COLLAPSED_LIMIT, favoriteEvents.size());

        for (int i = 0; i < max; i++) {
            FavoriteEvent fe = favoriteEvents.get(i);

            View row = inflater.inflate(R.layout.item_favorite_event_profile, container, false);

            TextView tvTitle = row.findViewById(R.id.tvEventTitle);
            TextView tvLocation = row.findViewById(R.id.tvEventLocation);

            if (tvTitle != null) {
                tvTitle.setText(fe.title);
            }

            if (tvLocation != null) {
                if (fe.location == null || fe.location.trim().isEmpty()) {
                    tvLocation.setVisibility(View.GONE);
                } else {
                    tvLocation.setText(fe.location);
                    tvLocation.setVisibility(View.VISIBLE);
                }
            }

            row.setOnClickListener(v -> {
                if (fe.id == null || fe.id.trim().isEmpty()) {
                    toast("Sự kiện này không còn tồn tại");
                    return;
                }

                Context ctx = v.getContext();
                Intent intent = new Intent(ctx, EventDetailActivity.class);
                intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, fe.id);
                ctx.startActivity(intent);
            });

            container.addView(row);
        }
    }


    private void showAvatarMenu() {
        final int titleColor = MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnSurface, 0);

        CharSequence[] items = colorizeAll(
                new CharSequence[]{"Xem ảnh đại diện", "Chọn ảnh khác"},
                titleColor
        );

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(colorize("Ảnh đại diện", titleColor))
                .setItems(items, (d, which) -> {
                    if (which == 0) showAvatarPreview();
                    else if (which == 1) showPickSourceSheet();
                })
                .create();
        dlg.show();
    }

    private void showPickSourceSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
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
        int onSurface         = MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnSurface, 0);
        int onSurfaceVariant  = MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0);

        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(pad, pad, pad, pad);
        row.setClickable(true);
        row.setFocusable(true);

        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        android.widget.ImageView iv = new android.widget.ImageView(requireContext());
        iv.setImageResource(icon);
        iv.setImageTintList(android.content.res.ColorStateList.valueOf(onSurfaceVariant));
        android.widget.LinearLayout.LayoutParams ip =
                new android.widget.LinearLayout.LayoutParams(dp(24), dp(24));
        ip.rightMargin = pad;
        row.addView(iv, ip);

        android.widget.TextView label = new android.widget.TextView(requireContext());
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(onSurface);
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
        int outline = MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOutline, 0);
        v.setBackgroundColor(outline);
        return v;
    }

    private void ensureReadThenPick() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        Context ctx = getContext();
        if (ctx == null) return;

        if (ContextCompat.checkSelfPermission(ctx, perm)
                == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*");
        } else {
            requestReadPerm.launch(perm);
        }
    }

    private void ensureCameraThenOpen() {
        String perm = Manifest.permission.CAMERA;
        Context ctx = getContext();
        if (ctx == null) return;

        if (ContextCompat.checkSelfPermission(ctx, perm)
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
        Context ctx = getContext();
        if (ctx == null) return null;
        File dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (dir == null) return null;
        File file = new File(dir, "avatar_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(
                ctx,
                ctx.getPackageName() + ".fileprovider",
                file
        );
    }

    private void showAvatarPreview() {
        final int titleColor = MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnSurface, 0);

        ImageView img = new ImageView(requireContext());
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        img.setPadding(pad, pad, pad, pad);

        ImageView current = getView() != null ? getView().findViewById(R.id.imgAvatar) : null;
        if (current != null && current.getDrawable() != null) {
            img.setImageDrawable(current.getDrawable());
        } else {
            img.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(colorize("Ảnh đại diện", titleColor))
                .setView(img)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void toast(String m) {
        Context c = getContext();
        if (c != null) Toast.makeText(c, m, Toast.LENGTH_SHORT).show();
    }

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

    private Uri copyToAppStorage(@NonNull Uri src) {
        Context ctx = getContext();
        if (ctx == null) return null;
        InputStream in = null;
        OutputStream out = null;
        try {
            File dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            if (dir == null) return null;
            File dst = new File(dir, "avatar_" + System.currentTimeMillis() + ".jpg");

            in = ctx.getContentResolver().openInputStream(src);
            if (in == null) return null;
            out = new java.io.FileOutputStream(dst);

            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();

            return FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".fileprovider", dst
            );
        } catch (Exception ignore) {
            return null;
        } finally {
            try { if (in  != null) in.close();  } catch (Exception ignore) {}
            try { if (out != null) out.close(); } catch (Exception ignore) {}
        }
    }

    private void loadBasicUserInfoIntoPanel(@NonNull View root) {
        if (!AuthManager.isLoggedIn(requireContext())) {
            if (edtProfileName != null)  edtProfileName.setText("");
            if (edtProfileEmail != null) edtProfileEmail.setText("");
            if (edtProfilePhone != null) edtProfilePhone.setText("");
            setProfileFieldsEditable(false);
            isEditingInfo = false;
            if (btnEditInfo != null) btnEditInfo.setText("Chỉnh sửa thông tin");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    String name  = doc.getString("name");
                    String email = doc.getString("email");
                    String phone = doc.getString("phone");

                    if (edtProfileName != null)  edtProfileName.setText(name != null ? name : "");
                    if (edtProfileEmail != null) edtProfileEmail.setText(email != null ? email : "");
                    if (edtProfilePhone != null) edtProfilePhone.setText(phone != null ? phone : "");

                    // Email luôn khóa
                    if (edtProfileEmail != null) {
                        edtProfileEmail.setEnabled(false);
                    }

                    // Mặc định ở trạng thái xem, chưa edit
                    isEditingInfo = false;
                    setProfileFieldsEditable(false);
                    if (btnEditInfo != null) btnEditInfo.setText("Chỉnh sửa thông tin");
                });
    }

    private void onEditInfoClick() {
        if (!AuthManager.isLoggedIn(requireContext())) {
            NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
            return;
        }

        if (!isEditingInfo) {
            isEditingInfo = true;
            setProfileFieldsEditable(true);
            if (btnEditInfo != null) btnEditInfo.setText("Lưu thông tin");
        } else {
            String name = edtProfileName != null && edtProfileName.getText() != null
                    ? edtProfileName.getText().toString().trim()
                    : "";
            String phone = edtProfilePhone != null && edtProfilePhone.getText() != null
                    ? edtProfilePhone.getText().toString().trim()
                    : "";

            if (name.isEmpty()) {
                toast("Tên không được để trống");
                return;
            }
            if (phone.isEmpty() || phone.length() < 9) {
                toast("Số điện thoại không hợp lệ");
                return;
            }

            updateBasicInfoInFirestore(name, phone);
        }
    }

    private void setProfileFieldsEditable(boolean editable) {
        // Chỉ cho sửa tên + phone, email luôn khóa
        if (edtProfileName != null)  edtProfileName.setEnabled(editable);
        if (edtProfilePhone != null) edtProfilePhone.setEnabled(editable);

        if (edtProfileEmail != null) edtProfileEmail.setEnabled(false);
    }

    private void updateBasicInfoInFirestore(String name, String phone) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("phone", phone);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(data)
                .addOnSuccessListener(unused -> {
                    // update local cache (name)
                    String emailCache = AuthManager.getEmail(requireContext());
                    AuthManager.login(requireContext(), name, emailCache != null ? emailCache : "");

                    toast("Cập nhật thông tin thành công!");

                    isEditingInfo = false;
                    setProfileFieldsEditable(false);
                    if (btnEditInfo != null) btnEditInfo.setText("Chỉnh sửa thông tin");

                    View root = getView();
                    if (root != null) {
                        updateUi(root);
                    }
                })
                .addOnFailureListener(e -> {
                    toast("Lỗi cập nhật: " + e.getMessage());
                });
    }

    private void showAddCardDialog(@NonNull TextView tvPrimaryCard) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View view = inflater.inflate(R.layout.dialog_add_card, null);

        TextInputEditText edtHolder = view.findViewById(R.id.edtCardHolder);
        TextInputEditText edtNumber = view.findViewById(R.id.edtCardNumber);
        TextInputEditText edtExpiry = view.findViewById(R.id.edtCardExpiry);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Thêm thẻ thanh toán")
                .setView(view)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String holder = edtHolder.getText() != null
                            ? edtHolder.getText().toString().trim() : "";
                    String number = edtNumber.getText() != null
                            ? edtNumber.getText().toString().trim() : "";
                    String expiry = edtExpiry.getText() != null
                            ? edtExpiry.getText().toString().trim() : "";

                    if (number.length() < 4) {
                        toast("Số thẻ không hợp lệ");
                        return;
                    }

                    String last4 = number.substring(number.length() - 4);
                    String mask = "**** **** **** " + last4;

                    if (!holder.isEmpty()) {
                        mask = holder + " - " + mask;
                    }
                    if (!expiry.isEmpty()) {
                        mask = mask + " (" + expiry + ")";
                    }

                    SharedPreferences prefs = requireContext()
                            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putString("primary_card_mask", mask).apply();

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        String uid = user.getUid();
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .update("primaryCardMask", mask)
                                .addOnFailureListener(e -> {
                                    toast("Lưu thẻ lên server thất bại: " + e.getMessage());
                                });
                    }

                    tvPrimaryCard.setText("• " + mask);
                    toast("Đã lưu thẻ");
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

}
